package br.com.consignado.platform.worker.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.worker.application.usecase.RetryBackoff;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryBackoffTest {

  private final RetryBackoff backoff =
      new RetryBackoff(Duration.ofSeconds(5), Duration.ofSeconds(60));

  @Test
  void should_double_the_delay_on_each_delivery() {
    assertThat(backoff.delay(1)).isEqualTo(Duration.ofSeconds(5));
    assertThat(backoff.delay(2)).isEqualTo(Duration.ofSeconds(10));
    assertThat(backoff.delay(3)).isEqualTo(Duration.ofSeconds(20));
    assertThat(backoff.delay(4)).isEqualTo(Duration.ofSeconds(40));
  }

  @Test
  void should_cap_the_delay_at_the_maximum() {
    assertThat(backoff.delay(5)).isEqualTo(Duration.ofSeconds(60));
    assertThat(backoff.delay(500)).isEqualTo(Duration.ofSeconds(60));
  }

  @Test
  void should_treat_non_positive_counts_as_the_first_delivery() {
    assertThat(backoff.delay(0)).isEqualTo(Duration.ofSeconds(5));
    assertThat(backoff.delay(-3)).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void should_reject_an_invalid_configuration() {
    assertThatThrownBy(() -> new RetryBackoff(Duration.ofSeconds(10), Duration.ofSeconds(5)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RetryBackoff(Duration.ofSeconds(-1), Duration.ofSeconds(5)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
