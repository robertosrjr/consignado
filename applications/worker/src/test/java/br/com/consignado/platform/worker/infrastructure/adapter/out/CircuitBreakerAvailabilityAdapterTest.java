package br.com.consignado.platform.worker.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.worker.application.port.out.LegacyAvailabilityPort.Availability;
import br.com.consignado.platform.worker.infrastructure.adapter.out.legacy.CircuitBreakerAvailabilityAdapter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

class CircuitBreakerAvailabilityAdapterTest {

  private final CircuitBreaker breaker = CircuitBreaker.ofDefaults("legacy");
  private final CircuitBreakerAvailabilityAdapter adapter =
      new CircuitBreakerAvailabilityAdapter(breaker);

  @Test
  void should_be_available_while_the_circuit_is_closed() {
    assertThat(adapter.availability()).isEqualTo(Availability.AVAILABLE);
  }

  @Test
  void should_be_unavailable_while_the_circuit_is_open() {
    breaker.transitionToOpenState();
    assertThat(adapter.availability()).isEqualTo(Availability.UNAVAILABLE);

    breaker.transitionToForcedOpenState();
    assertThat(adapter.availability()).isEqualTo(Availability.UNAVAILABLE);
  }

  @Test
  void should_probe_while_the_circuit_is_half_open() {
    breaker.transitionToOpenState();
    breaker.transitionToHalfOpenState();

    assertThat(adapter.availability()).isEqualTo(Availability.PROBING);
  }
}
