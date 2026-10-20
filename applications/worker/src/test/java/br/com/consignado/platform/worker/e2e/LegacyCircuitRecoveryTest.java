package br.com.consignado.platform.worker.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.contract.ProposalMessage;
import br.com.consignado.platform.shared.vo.Cpf;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.shared.vo.TenantId;
import br.com.consignado.platform.worker.application.port.in.ProcessingOutcome;
import br.com.consignado.platform.worker.application.port.out.BudgetSnapshot;
import br.com.consignado.platform.worker.application.port.out.DispatchObserver;
import br.com.consignado.platform.worker.application.port.out.LegacySubmission;
import br.com.consignado.platform.worker.application.port.out.ThroughputBudgetPort;
import br.com.consignado.platform.worker.application.usecase.ProposalDispatchLoop;
import br.com.consignado.platform.worker.application.usecase.RetryBackoff;
import br.com.consignado.platform.worker.domain.LaneBudgetAllocator;
import br.com.consignado.platform.worker.domain.LegacyResult;
import br.com.consignado.platform.worker.infrastructure.adapter.out.legacy.CircuitBreakerAvailabilityAdapter;
import br.com.consignado.platform.worker.infrastructure.adapter.out.legacy.LegacyHttpAdapter;
import br.com.consignado.platform.worker.infrastructure.adapter.out.messaging.InMemoryLaneQueue;
import br.com.consignado.platform.worker.support.StubLegacy;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Local L3 rehearsal: real HTTP and breaker, memory queue, synchronous processing seam. */
class LegacyCircuitRecoveryTest {
  @Test
  void should_preserve_deliveries_while_open_and_recover_the_same_cohort() throws Exception {
    try (var legacy = StubLegacy.start()) {
      var clock = new AtomicLong();
      var queue = new InMemoryLaneQueue(3, clock::get);
      var keys = new ArrayList<IdempotencyKey>();
      for (int i = 0; i < 20; i++) {
        var key = new IdempotencyKey(UUID.randomUUID());
        keys.add(key);
        queue.enqueue(Lane.FAST, ProposalMessage.of(key, new TenantId("banco-a"), Lane.FAST, 60));
      }
      // Same opening thresholds and automatic 10-second probing as the default worker.
      var config =
          CircuitBreaker.of(
              "l3-local",
              LegacyHttpAdapter.breakerConfig(
                  10, Duration.ofSeconds(2), 20, 100, Duration.ofSeconds(10)));
      var transitions = new ArrayList<CircuitBreaker.StateTransition>();
      config.getEventPublisher().onStateTransition(e -> transitions.add(e.getStateTransition()));
      var client =
          new LegacyHttpAdapter(
              HttpClient.newHttpClient(),
              URI.create(legacy.baseUrl()),
              "test",
              Duration.ofSeconds(10),
              config);
      var receives = new ArrayList<Integer>();
      var budget =
          new ThroughputBudgetPort() {
            public BudgetSnapshot snapshot() {
              var lane = new BudgetSnapshot.LaneBudget(true, 1);
              return new BudgetSnapshot(true, lane, lane);
            }

            public void consume(Lane lane) {}
          };
      var loop =
          new ProposalDispatchLoop(
              queue,
              budget,
              message -> {
                var result =
                    client.submit(
                        new LegacySubmission(
                            message.key(),
                            new Cpf("52998224725"),
                            ConvenioType.INSS,
                            Money.of("1000.00"),
                            12));
                return result instanceof LegacyResult.Confirmed
                    ? ProcessingOutcome.COMPLETED
                    : ProcessingOutcome.RETRY;
              },
              new LaneBudgetAllocator(),
              Runnable::run,
              1,
              new RetryBackoff(Duration.ofSeconds(5), Duration.ofSeconds(60)),
              DispatchObserver.NONE,
              new CircuitBreakerAvailabilityAdapter(config));

      legacy.status = 503;
      for (int i = 0; i < 20; i++) {
        assertThat(loop.runOnce()).isTrue();
      }
      assertThat(config.getState()).isEqualTo(CircuitBreaker.State.OPEN);
      assertThat(legacy.requests.get()).isEqualTo(20);
      clock.addAndGet(Duration.ofSeconds(60).toNanos());
      for (int i = 0; i < 100; i++) {
        assertThat(loop.runOnce()).isFalse();
      }
      assertThat(legacy.requests.get()).isEqualTo(20);
      assertThat(queue.deadLetters()).isEmpty();
      assertThat(queue.acknowledgedCount()).isZero();
      // Inspect and requeue to prove OPEN did not consume additional deliveries.
      for (int i = 0; i < 20; i++) {
        var message = queue.poll(Lane.FAST).orElseThrow();
        receives.add(message.receiveCount());
        queue.release(message, Duration.ZERO);
      }
      assertThat(receives).containsOnly(2);
      // Inspection consumed a delivery: use no further inspection before completion.
      legacy.status = 200;
      long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while (config.getState() == CircuitBreaker.State.OPEN && System.nanoTime() < deadline) {
        Thread.sleep(25);
      }
      assertThat(config.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
      for (int i = 0; i < 5; i++) {
        assertThat(loop.runOnce()).isTrue();
        assertThat(config.getState())
            .isEqualTo(i < 4 ? CircuitBreaker.State.HALF_OPEN : CircuitBreaker.State.CLOSED);
      }
      for (int i = 5; i < 20; i++) {
        assertThat(loop.runOnce()).isTrue();
      }
      assertThat(queue.acknowledgedCount()).isEqualTo(20);
      assertThat(queue.deadLetters()).isEmpty();
      assertThat(queue.hasMessages(Lane.FAST)).isFalse();
      assertThat(legacy.uniqueKeys)
          .containsExactlyInAnyOrderElementsOf(keys.stream().map(Object::toString).toList());
      assertThat(legacy.duplicateRequests.get()).isZero();
      assertThat(legacy.requests.get()).isEqualTo(40);
      assertThat(transitions)
          .containsExactly(
              CircuitBreaker.StateTransition.CLOSED_TO_OPEN,
              CircuitBreaker.StateTransition.OPEN_TO_HALF_OPEN,
              CircuitBreaker.StateTransition.HALF_OPEN_TO_CLOSED);
    }
  }
}
