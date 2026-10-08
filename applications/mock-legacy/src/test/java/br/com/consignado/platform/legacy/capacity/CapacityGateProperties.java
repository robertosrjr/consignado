package br.com.consignado.platform.legacy.capacity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;

/**
 * Oraculo (PBT-05): o gate deve concordar com um modelo ingenuo de janela deslizante; e invariante
 * (PBT-03): nunca ha mais que {@code limit} entradas aceitas em qualquer janela de 1 segundo.
 */
class CapacityGateProperties {

  private static final long SECOND = 1_000_000_000L;

  @Property
  void gate_matches_the_naive_sliding_window_model(
      @ForAll @IntRange(min = 1, max = 50) int limit,
      @ForAll @Size(max = 600) List<@LongRange(min = 0, max = 60_000_000L) Long> gapsNanos) {
    long[] clock = {10 * SECOND};
    CapacityGate gate = new CapacityGate(limit, () -> clock[0]);
    List<Long> accepted = new ArrayList<>();

    for (long gap : gapsNanos) {
      clock[0] += gap;
      long now = clock[0];
      long inWindow = accepted.stream().filter(t -> now - t < SECOND).count();
      boolean expected = inWindow < limit;

      assertThat(gate.tryEnter()).isEqualTo(expected);
      if (expected) {
        accepted.add(now);
      }
    }
  }

  @Property
  void no_one_second_window_ever_holds_more_than_the_limit(
      @ForAll @IntRange(min = 1, max = 30) int limit,
      @ForAll @Size(max = 800) List<@LongRange(min = 0, max = 20_000_000L) Long> gapsNanos) {
    long[] clock = {SECOND};
    CapacityGate gate = new CapacityGate(limit, () -> clock[0]);
    List<Long> accepted = new ArrayList<>();

    for (long gap : gapsNanos) {
      clock[0] += gap;
      if (gate.tryEnter()) {
        accepted.add(clock[0]);
      }
    }

    for (long end : accepted) {
      long count = accepted.stream().filter(t -> t <= end && end - t < SECOND).count();
      assertThat(count).isLessThanOrEqualTo(limit);
    }
    assertThat(gate.peak()).isLessThanOrEqualTo(limit);
  }
}
