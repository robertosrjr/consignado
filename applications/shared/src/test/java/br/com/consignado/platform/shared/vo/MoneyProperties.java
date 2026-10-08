package br.com.consignado.platform.shared.vo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.testkit.DomainArbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Invariantes e comutatividade de {@link Money} (PBT-03). */
class MoneyProperties {

  @Provide
  Arbitrary<Money> moneys() {
    return DomainArbitraries.money();
  }

  @Property
  void amount_is_never_negative_and_always_has_scale_two(@ForAll("moneys") Money money) {
    assertThat(money.amount().signum()).isGreaterThanOrEqualTo(0);
    assertThat(money.amount().scale()).isEqualTo(2);
    assertThat(money.amount()).isLessThanOrEqualTo(Money.MAX);
  }

  @Property
  void string_round_trip_preserves_the_value(@ForAll("moneys") Money money) {
    assertThat(Money.of(money.amount().toPlainString())).isEqualTo(money);
  }

  @Property
  void addition_is_commutative_when_the_sum_is_representable(
      @ForAll("moneys") Money a, @ForAll("moneys") Money b) {
    Assume.that(a.amount().add(b.amount()).compareTo(Money.MAX) <= 0);

    assertThat(a.add(b)).isEqualTo(b.add(a));
  }

  @Property
  void adding_zero_is_identity(@ForAll("moneys") Money money) {
    assertThat(money.add(Money.of("0"))).isEqualTo(money);
  }
}
