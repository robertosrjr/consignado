package br.com.consignado.platform.worker.infrastructure.metrics;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.worker.application.port.in.ProcessingOutcome;
import br.com.consignado.platform.worker.application.port.out.DispatchObserver;
import br.com.consignado.platform.worker.domain.ProposalStatus;
import br.com.consignado.platform.worker.infrastructure.adapter.out.persistence.InMemoryProposalRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metricas do Worker: despachos e resultados por lane, propostas por estado (evidencia do DoD de
 * idempotencia), estado do circuit breaker e as metricas de backlog ({@code net.backlog.growth},
 * {@code effective.drain.time}). Tags de baixa cardinalidade (lane, estado, resultado).
 */
public final class WorkerMetrics implements DispatchObserver {

  private final MeterRegistry registry;
  private final AtomicLong completed = new AtomicLong();

  public WorkerMetrics(
      MeterRegistry registry,
      InMemoryProposalRepository repository,
      BacklogEstimator estimator,
      CircuitBreaker breaker) {
    this.registry = registry;
    for (ProposalStatus status : ProposalStatus.values()) {
      Gauge.builder("worker.proposals", repository, r -> r.count(status))
          .description("Propostas por estado")
          .tag("status", status.name())
          .register(registry);
    }
    Gauge.builder("worker.legacy.circuit.state", breaker, WorkerMetrics::stateCode)
        .description("Estado do circuit breaker do legado (0 fechado, 1 semi-aberto, 2 aberto)")
        .register(registry);
    Gauge.builder("net.backlog.growth", estimator, BacklogEstimator::netBacklogGrowth)
        .description("Taxa de chegada menos taxa de processamento (mensagens/s)")
        .register(registry);
    Gauge.builder("effective.drain.time", estimator, BacklogEstimator::effectiveDrainTimeSeconds)
        .description("Profundidade das filas dividida pela taxa de processamento (s)")
        .baseUnit("seconds")
        .register(registry);
    Gauge.builder("worker.processing.rate", estimator, BacklogEstimator::processingRate)
        .register(registry);
    Gauge.builder("worker.arrival.rate", estimator, BacklogEstimator::arrivalRate)
        .register(registry);
  }

  @Override
  public void dispatched(Lane lane) {
    registry.counter("worker.dispatched", "lane", lane.name().toLowerCase(Locale.ROOT)).increment();
  }

  @Override
  public void processed(Lane lane, ProcessingOutcome outcome) {
    registry
        .counter(
            "worker.processed",
            "lane",
            lane.name().toLowerCase(Locale.ROOT),
            "outcome",
            outcome.name().toLowerCase(Locale.ROOT))
        .increment();
    if (outcome == ProcessingOutcome.COMPLETED) {
      completed.incrementAndGet();
    }
  }

  @Override
  public void failed(Lane lane) {
    registry
        .counter("worker.processing.failed", "lane", lane.name().toLowerCase(Locale.ROOT))
        .increment();
  }

  /** Total concluido com sucesso (entrada do estimador de backlog). */
  public long completedTotal() {
    return completed.get();
  }

  private static double stateCode(CircuitBreaker breaker) {
    return switch (breaker.getState()) {
      case CLOSED -> 0;
      case HALF_OPEN -> 1;
      case OPEN, FORCED_OPEN -> 2;
      default -> 3;
    };
  }
}
