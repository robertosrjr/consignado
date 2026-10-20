package br.com.consignado.platform.legacy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class LegacyFaultPolicyTest {

  private final AtomicLong clock = new AtomicLong();
  private final LegacyFaultPolicy policy =
      new LegacyFaultPolicy(
          new LegacyProperties("test", 180, 1000, new LegacyProperties.Chaos(0, 0)), clock::get);

  @Test
  void expires_at_deadline_without_a_restore_request_or_scheduler() {
    assertThat(policy.activate(4000, 0, 100).active()).isTrue();
    clock.set(TimeUnit.MILLISECONDS.toNanos(99));
    assertThat(policy.state().latencyMs()).isEqualTo(4000);
    clock.set(TimeUnit.MILLISECONDS.toNanos(100));
    assertThat(policy.state()).isEqualTo(new LegacyFaultPolicy.State(false, 0, 0, 0));
    assertThat(policy.activate(0, 1, 10).active()).isTrue();
  }

  @Test
  void restores_idempotently_and_does_not_silently_extend_an_active_fault() {
    policy.activate(0, 1, 100);
    assertThatIllegalStateException().isThrownBy(() -> policy.activate(1, 0, 100));
    assertThat(policy.restore().active()).isFalse();
    assertThat(policy.restore().errorRate()).isZero();
  }

  @Test
  void retains_the_static_baseline_outside_a_controlled_experiment() {
    var configured =
        new LegacyFaultPolicy(
            new LegacyProperties("test", 180, 1000, new LegacyProperties.Chaos(2, 0.5)),
            clock::get);
    configured.activate(0, 1, 1);
    clock.set(TimeUnit.MILLISECONDS.toNanos(1));
    assertThat(configured.state()).isEqualTo(new LegacyFaultPolicy.State(false, 2, 0.5, 0));
  }

  @Test
  void rejects_unbounded_non_finite_and_no_effect_requests() {
    long[][] invalid = {{-1, 1}, {10_001, 1}, {1, 0}, {1, 300_001}, {0, 10}};
    for (long[] request : invalid) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> policy.activate(request[0], 0, request[1]));
    }
    for (double error : new double[] {-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThatIllegalArgumentException().isThrownBy(() -> policy.activate(0, error, 1));
    }
    assertThat(policy.state().active()).isFalse();
    assertThat(policy.activate(10_000, 1, 300_000).active()).isTrue();
  }
}
