package br.com.consignado.platform.shared.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void should_normalize_scale_to_two_places() {
    assertThat(Money.of("15000.5").amount()).isEqualTo(new BigDecimal("15000.50"));
    assertThat(Money.of("0").amount()).isEqualTo(new BigDecimal("0.00"));
  }

  @Test
  void should_treat_equivalent_values_as_equal() {
    assertThat(Money.of("10")).isEqualTo(Money.of("10.00"));
  }

  @Test
  void should_add_amounts() {
    assertThat(Money.of("100.10").add(Money.of("50.05"))).isEqualTo(Money.of("150.15"));
  }

  @Test
  void should_reject_negative_amount() {
    assertThatThrownBy(() -> Money.of("-0.01")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_more_than_two_decimal_places_without_rounding() {
    assertThatThrownBy(() -> Money.of("10.001")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_amount_above_numeric_12_2_limit() {
    assertThat(Money.of("9999999999.99")).isNotNull();
    assertThatThrownBy(() -> Money.of("10000000000.00"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Money.of("9999999999.99").add(Money.of("0.01")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_null_and_garbage() {
    assertThatThrownBy(() -> Money.of(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Money.of("abc")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Money(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_compare_amounts() {
    assertThat(Money.of("2").isGreaterThan(Money.of("1.99"))).isTrue();
    assertThat(Money.of("1").isGreaterThan(Money.of("1.00"))).isFalse();
  }
}
