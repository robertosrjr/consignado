package br.com.consignado.platform.worker.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;
import br.com.consignado.platform.worker.application.port.in.ProcessingOutcome;
import br.com.consignado.platform.worker.application.port.out.ProposalSeed;
import br.com.consignado.platform.worker.domain.ProposalStatus;
import br.com.consignado.platform.worker.infrastructure.adapter.out.persistence.InMemoryProposalRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MetricsTest {

  private static final long SECOND = 1_000_000_000L;

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final InMemoryProposalRepository repository =
      new InMemoryProposalRepository(() -> Instant.parse("2026-10-08T12:00:00Z"));
  private final BacklogEstimator estimator = new BacklogEstimator();
  private final CircuitBreaker breaker = CircuitBreaker.ofDefaults("teste");
  private final WorkerMetrics metrics = new WorkerMetrics(registry, repository, estimator, breaker);

  @Test
  void should_estimate_nothing_before_the_second_sample() {
    estimator.sample(100, 0, 10 * SECOND);

    assertThat(estimator.processingRate()).isZero();
    assertThat(estimator.netBacklogGrowth()).isZero();
  }

  @Test
  void should_compute_growth_arrival_processing_and_drain_time() {
    estimator.sample(1_000, 0, 10 * SECOND);

    // em 5 s: processou 900 (180/s) e a profundidade subiu de 1000 para 3000 (+400/s)
    estimator.sample(3_000, 900, 15 * SECOND);

    assertThat(estimator.processingRate()).isEqualTo(180.0);
    assertThat(estimator.netBacklogGrowth()).isEqualTo(400.0);
    assertThat(estimator.arrivalRate()).isEqualTo(580.0);
    assertThat(estimator.effectiveDrainTimeSeconds()).isEqualTo(3_000 / 180.0);
  }

  @Test
  void should_report_a_negative_growth_while_the_backlog_drains() {
    estimator.sample(10_000, 0, 0);

    estimator.sample(9_100, 900, 5 * SECOND);

    assertThat(estimator.netBacklogGrowth()).isEqualTo(-180.0);
    assertThat(estimator.arrivalRate()).isZero();
  }

  @Test
  void should_report_zero_drain_time_for_empty_queues_and_infinity_when_nothing_is_processed() {
    estimator.sample(0, 0, 0);
    estimator.sample(0, 0, 5 * SECOND);
    assertThat(estimator.effectiveDrainTimeSeconds()).isZero();

    estimator.sample(500, 0, 10 * SECOND);
    assertThat(estimator.effectiveDrainTimeSeconds()).isInfinite();
  }

  @Test
  void should_ignore_samples_that_do_not_advance_time() {
    estimator.sample(100, 0, 10 * SECOND);
    estimator.sample(200, 50, 10 * SECOND);

    assertThat(estimator.processingRate()).isZero();
  }

  @Test
  void should_count_dispatch_and_processing_results_by_lane_and_outcome() {
    metrics.dispatched(Lane.FAST);
    metrics.processed(Lane.FAST, ProcessingOutcome.COMPLETED);
    metrics.processed(Lane.STANDARD, ProcessingOutcome.RETRY);
    metrics.failed(Lane.STANDARD);

    assertThat(registry.get("worker.dispatched").tag("lane", "fast").counter().count())
        .isEqualTo(1);
    assertThat(
            registry
                .get("worker.processed")
                .tag("lane", "fast")
                .tag("outcome", "completed")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get("worker.processed")
                .tag("lane", "standard")
                .tag("outcome", "retry")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(registry.get("worker.processing.failed").tag("lane", "standard").counter().count())
        .isEqualTo(1);
    assertThat(metrics.completedTotal()).isEqualTo(1);
  }

  @Test
  void should_expose_proposals_by_status_and_the_circuit_breaker_state() {
    repository.createPendingIfAbsent(
        new ProposalSeed(
            new IdempotencyKey(UUID.randomUUID()), new TenantId("banco-a"), Lane.FAST));

    assertThat(
            registry
                .get("worker.proposals")
                .tag("status", ProposalStatus.PENDING.name())
                .gauge()
                .value())
        .isEqualTo(1.0);
    assertThat(registry.get("worker.legacy.circuit.state").gauge().value()).isZero();
    breaker.transitionToOpenState();
    assertThat(registry.get("worker.legacy.circuit.state").gauge().value()).isEqualTo(2.0);
  }

  @Test
  void should_sample_queue_depths_and_publish_backlog_gauges() {
    long[] depth = {600, 400};
    QueueDepthSampler sampler =
        new QueueDepthSampler(
            registry, lane -> lane == Lane.FAST ? depth[0] : depth[1], metrics, estimator, 5);

    sampler.sampleNow(0);
    depth[0] = 700;
    depth[1] = 500;
    sampler.sampleNow(10 * SECOND);

    assertThat(registry.get("worker.queue.depth").tag("lane", "fast").gauge().value())
        .isEqualTo(700);
    assertThat(registry.get("worker.queue.depth").tag("lane", "standard").gauge().value())
        .isEqualTo(500);
    assertThat(registry.get("net.backlog.growth").gauge().value()).isEqualTo(20.0);
    assertThat(sampler.isRunning()).isFalse();
  }

  @Test
  void should_start_and_stop_the_sampler_lifecycle() {
    QueueDepthSampler sampler = new QueueDepthSampler(registry, lane -> 0, metrics, estimator, 1);

    sampler.start();
    assertThat(sampler.isRunning()).isTrue();
    sampler.stop();

    assertThat(sampler.isRunning()).isFalse();
  }
}
