package br.com.consignado.platform.legacy.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CapacityGateTest {

  private static final long SECOND = 1_000_000_000L;

  private final AtomicLong now = new AtomicLong(5 * SECOND);

  @Test
  void should_accept_up_to_the_limit_inside_one_second_and_reject_the_rest() {
    CapacityGate gate = new CapacityGate(3, now::get);

    assertThat(gate.tryEnter()).isTrue();
    assertThat(gate.tryEnter()).isTrue();
    assertThat(gate.tryEnter()).isTrue();
    assertThat(gate.tryEnter()).isFalse();
    assertThat(gate.current()).isEqualTo(3);
    assertThat(gate.peak()).isEqualTo(3);
  }

  @Test
  void should_free_capacity_exactly_one_second_after_the_entry() {
    CapacityGate gate = new CapacityGate(1, now::get);
    assertThat(gate.tryEnter()).isTrue();

    now.addAndGet(SECOND - 1);
    assertThat(gate.tryEnter()).isFalse();

    now.addAndGet(1);
    assertThat(gate.tryEnter()).isTrue();
  }

  @Test
  void should_never_report_a_peak_above_the_limit() {
    CapacityGate gate = new CapacityGate(2, now::get);
    for (int i = 0; i < 100; i++) {
      gate.tryEnter();
    }

    assertThat(gate.peak()).isEqualTo(2);
    assertThat(gate.limit()).isEqualTo(2);
  }

  @Test
  void should_reject_invalid_limit() {
    assertThatThrownBy(() -> new CapacityGate(0, now::get))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
