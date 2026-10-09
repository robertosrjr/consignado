package br.com.consignado.platform.worker.infrastructure.adapter.out.legacy;

import br.com.consignado.platform.worker.application.port.out.LegacyAvailabilityPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * Traduz o estado do circuit breaker do legado em disponibilidade para o laco de despacho. Protecao
 * local ao processo: cada instancia do Worker observa o proprio circuito.
 */
public final class CircuitBreakerAvailabilityAdapter implements LegacyAvailabilityPort {

  private final CircuitBreaker breaker;

  public CircuitBreakerAvailabilityAdapter(CircuitBreaker breaker) {
    this.breaker = breaker;
  }

  @Override
  public Availability availability() {
    return switch (breaker.getState()) {
      case OPEN, FORCED_OPEN -> Availability.UNAVAILABLE;
      case HALF_OPEN -> Availability.PROBING;
      case CLOSED, DISABLED, METRICS_ONLY -> Availability.AVAILABLE;
    };
  }
}
