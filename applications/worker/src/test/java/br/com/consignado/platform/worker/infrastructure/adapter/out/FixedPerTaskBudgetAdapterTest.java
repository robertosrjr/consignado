package br.com.consignado.platform.worker.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.worker.application.port.out.BudgetSnapshot;
import br.com.consignado.platform.worker.infrastructure.adapter.out.budget.FixedPerTaskBudgetAdapter;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FixedPerTaskBudgetAdapterTest {

  private static final long SECOND = 1_000_000_000L;

  private final AtomicLong now = new AtomicLong(10 * SECOND);

  private FixedPerTaskBudgetAdapter budget(int instances) {
    return new FixedPerTaskBudgetAdapter(180, instances, 0.70, 0.98, 2, now::get);
  }

  @Test
  void should_start_ready_on_every_budget() {
    BudgetSnapshot snapshot = budget(1).snapshot();

    assertThat(snapshot.globalReady()).isTrue();
    assertThat(snapshot.fast().floorReady()).isTrue();
    assertThat(snapshot.standard().floorReady()).isTrue();
  }

  @Test
  void should_exhaust_the_global_budget_after_the_burst_capacity() {
    FixedPerTaskBudgetAdapter budget = budget(1);

    budget.consume(Lane.FAST);
    budget.consume(Lane.FAST);

    assertThat(budget.snapshot().globalReady()).isFalse();
  }

  @Test
  void should_refill_the_global_budget_at_the_safety_margin_rate() {
    FixedPerTaskBudgetAdapter budget = budget(1);
    budget.consume(Lane.FAST);
    budget.consume(Lane.STANDARD);

    now.addAndGet((long) (SECOND / (180 * 0.98)) - 1_000);
    assertThat(budget.snapshot().globalReady()).isFalse();
    now.addAndGet(2_000);
    assertThat(budget.snapshot().globalReady()).isTrue();
  }

  @Test
  void should_split_the_ceiling_among_instances() {
    FixedPerTaskBudgetAdapter budget = budget(3);
    budget.consume(Lane.FAST);
    budget.consume(Lane.FAST);

    // 180 * 0.98 / 3 = 58.8 por segundo: apos 10 ms ainda nao ha nova permissao global
    now.addAndGet(10_000_000L);
    assertThat(budget.snapshot().globalReady()).isFalse();
    now.addAndGet(10_000_000L);
    assertThat(budget.snapshot().globalReady()).isTrue();
  }

  @Test
  void should_keep_lane_credit_independent_per_lane() {
    FixedPerTaskBudgetAdapter budget = budget(1);
    budget.consume(Lane.FAST);
    budget.consume(Lane.FAST);

    BudgetSnapshot snapshot = budget.snapshot();
    assertThat(snapshot.fast().credit()).isLessThan(snapshot.standard().credit());
  }

  @Test
  void should_reject_invalid_parameters() {
    assertThatThrownBy(() -> new FixedPerTaskBudgetAdapter(0, 1, 0.7, 0.98, 2, now::get))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FixedPerTaskBudgetAdapter(180, 0, 0.7, 0.98, 2, now::get))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FixedPerTaskBudgetAdapter(180, 1, 1.0, 0.98, 2, now::get))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FixedPerTaskBudgetAdapter(180, 1, 0.7, 1.5, 2, now::get))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
