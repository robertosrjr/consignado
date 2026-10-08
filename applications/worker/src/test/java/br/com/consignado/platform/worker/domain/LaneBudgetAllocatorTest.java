package br.com.consignado.platform.worker.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.worker.domain.LaneBudgetAllocator.LaneView;
import org.junit.jupiter.api.Test;

class LaneBudgetAllocatorTest {

  private final LaneBudgetAllocator allocator = new LaneBudgetAllocator();

  private static LaneView view(boolean messages, boolean floor, double credit) {
    return new LaneView(messages, floor, credit);
  }

  @Test
  void should_pick_nothing_when_the_global_budget_is_exhausted() {
    assertThat(allocator.pick(view(true, true, 2), view(true, true, 2), false)).isEmpty();
  }

  @Test
  void should_pick_nothing_when_no_lane_has_messages() {
    assertThat(allocator.pick(view(false, true, 2), view(false, true, 2), true)).isEmpty();
  }

  @Test
  void should_prefer_fast_on_a_tie_when_both_lanes_are_ready() {
    assertThat(allocator.pick(view(true, true, 2), view(true, true, 2), true)).contains(Lane.FAST);
  }

  @Test
  void should_pick_the_lane_with_more_accumulated_credit() {
    assertThat(allocator.pick(view(true, true, 1.2), view(true, true, 1.9), true))
        .contains(Lane.STANDARD);
    assertThat(allocator.pick(view(true, true, 1.9), view(true, true, 1.2), true))
        .contains(Lane.FAST);
  }

  @Test
  void should_serve_the_lane_whose_floor_is_ready_when_the_other_floor_is_exhausted() {
    assertThat(allocator.pick(view(true, false, 0.2), view(true, true, 1.5), true))
        .contains(Lane.STANDARD);
    assertThat(allocator.pick(view(true, true, 1.5), view(true, false, 0.2), true))
        .contains(Lane.FAST);
  }

  @Test
  void should_wait_when_both_have_messages_but_neither_floor_is_ready() {
    assertThat(allocator.pick(view(true, false, 0.4), view(true, false, 0.4), true)).isEmpty();
  }

  @Test
  void should_lend_capacity_to_fast_when_standard_is_empty() {
    assertThat(allocator.pick(view(true, false, 0.0), view(false, true, 2), true))
        .contains(Lane.FAST);
  }

  @Test
  void should_lend_capacity_to_standard_when_fast_is_empty() {
    assertThat(allocator.pick(view(false, true, 2), view(true, false, 0.0), true))
        .contains(Lane.STANDARD);
  }
}
