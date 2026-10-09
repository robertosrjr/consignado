package br.com.consignado.platform.worker.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.contract.ProposalMessage;
import br.com.consignado.platform.shared.payload.InboxPayload;
import br.com.consignado.platform.shared.vo.Cpf;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.shared.vo.TenantId;
import br.com.consignado.platform.worker.application.port.out.InboxRecord;
import br.com.consignado.platform.worker.domain.ProposalStatus;
import br.com.consignado.platform.worker.infrastructure.adapter.out.inbox.InMemoryInboxReadAdapter;
import br.com.consignado.platform.worker.infrastructure.adapter.out.messaging.InMemoryLaneQueue;
import br.com.consignado.platform.worker.infrastructure.adapter.out.persistence.InMemoryProposalRepository;
import br.com.consignado.platform.worker.support.StubLegacy;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Ponta a ponta do Worker com o contexto Spring completo: filas e inbox em memoria, legado simulado
 * em HTTP real. Verifica o fluxo (PENDING -> CONFIRMED), a idempotencia (nenhuma averbacao
 * duplicada, mensagens duplicadas sem nova chamada), o teto de vazao medido no proprio legado, a
 * recuperacao de falha transitoria e o descarte de mensagens sem item no inbox.
 */
@SpringBootTest(
    properties = {
      "worker.queue.type=memory",
      "worker.inbox.type=memory",
      "worker.legacy.api-key=chave-teste",
      "worker.budget.total-tps=500",
      // a fila em memoria respeita o atraso de reentrega; backoff curto para o teste
      "worker.queue.retry-base-delay=100ms",
      "worker.queue.retry-max-delay=500ms",
      "worker.metrics-sample-period-seconds=1"
    })
class WorkerEndToEndTest {

  private static final StubLegacy LEGACY = StubLegacy.start();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("worker.legacy.base-url", LEGACY::baseUrl);
  }

  @AfterAll
  static void stopLegacy() {
    LEGACY.close();
  }

  @Autowired InMemoryLaneQueue queue;
  @Autowired InMemoryInboxReadAdapter inbox;
  @Autowired InMemoryProposalRepository repository;
  @Autowired MeterRegistry registry;

  private static final int UNIQUE = 400;

  private static void await(BooleanSupplier condition, Duration timeout)
      throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      Thread.sleep(25);
    }
    assertThat(condition.getAsBoolean()).as("condicao atingida dentro do prazo").isTrue();
  }

  @Test
  void should_process_every_proposal_exactly_once_within_the_throughput_ceiling() throws Exception {
    TenantId tenant = new TenantId("banco-a");
    List<IdempotencyKey> keys = new ArrayList<>();
    for (int i = 0; i < UNIQUE; i++) {
      IdempotencyKey key = new IdempotencyKey(UUID.randomUUID());
      keys.add(key);
      inbox.put(
          new InboxRecord(
              key,
              tenant,
              InboxPayload.of(
                  new Cpf("52998224725"), ConvenioType.INSS, false, Money.of("1000.00"), 12),
              "trace-" + i));
      Lane lane = i % 2 == 0 ? Lane.FAST : Lane.STANDARD;
      queue.enqueue(lane, ProposalMessage.of(key, tenant, lane, lane == Lane.FAST ? 60 : 0));
    }
    // mensagens duplicadas das 20 primeiras chaves (repeticao de entrega)
    for (int i = 0; i < 20; i++) {
      queue.enqueue(Lane.FAST, ProposalMessage.of(keys.get(i), tenant, Lane.FAST, 60));
    }
    // uma chave que falha uma vez no legado (503) e depois confirma
    IdempotencyKey flaky = keys.get(100);
    LEGACY.failOnceFor.add(flaky.toString());
    // uma mensagem sem item no inbox: nunca conclui e vai para as mensagens mortas
    ProposalMessage orphan =
        ProposalMessage.of(new IdempotencyKey(UUID.randomUUID()), tenant, Lane.STANDARD, 0);
    queue.enqueue(Lane.STANDARD, orphan);

    await(() -> repository.count(ProposalStatus.CONFIRMED) == UNIQUE, Duration.ofSeconds(60));
    await(() -> queue.deadLetters().contains(orphan), Duration.ofSeconds(10));

    assertThat(repository.count(ProposalStatus.CONFIRMED)).isEqualTo(UNIQUE);
    assertThat(repository.count(ProposalStatus.PENDING)).isZero();
    assertThat(repository.count(ProposalStatus.SENT_TO_LEGACY)).isZero();
    assertThat(repository.count(ProposalStatus.FAILED)).isZero();

    // DoD: nenhuma averbacao duplicada no legado (uma por chave unica)
    assertThat(LEGACY.uniqueKeys).hasSize(UNIQUE);

    // DoD: o legado nunca recebeu mais que o orcamento por janela de 1 s (500 TPS no teste)
    assertThat(LEGACY.maxRequestsInAnyWindow(1_000_000_000L)).isLessThanOrEqualTo(500);

    // recuperacao da falha transitoria: o legado recebeu a mesma chave duas vezes (503 e sucesso).
    // O contador interno pode ser maior: uma recusa local do circuito, entre o despacho e o envio,
    // tambem conta como tentativa (distincao "antes do envio" proposta na M2).
    assertThat(LEGACY.requestsFor(flaky.toString())).isEqualTo(2);
    assertThat(repository.find(flaky).orElseThrow().attempts()).isGreaterThanOrEqualTo(2);
    assertThat(repository.find(flaky).orElseThrow().status()).isEqualTo(ProposalStatus.CONFIRMED);

    // mensagem sem item no inbox nao criou proposta
    assertThat(repository.find(orphan.key())).isEmpty();

    // metricas expostas para o k6 e os dashboards
    assertThat(registry.get("worker.proposals").tag("status", "CONFIRMED").gauge().value())
        .isEqualTo(UNIQUE);
    assertThat(
            registry.get("worker.processed").tag("outcome", "completed").counters().stream()
                .mapToDouble(c -> c.count())
                .sum())
        .isGreaterThanOrEqualTo(UNIQUE);
    assertThat(registry.find("worker.queue.depth").gauges()).isNotEmpty();
    assertThat(registry.find("net.backlog.growth").gauge()).isNotNull();
    assertThat(registry.find("effective.drain.time").gauge()).isNotNull();
  }
}
