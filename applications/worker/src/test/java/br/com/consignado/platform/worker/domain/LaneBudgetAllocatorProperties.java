package br.com.consignado.platform.worker.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.worker.domain.LaneBudgetAllocator.LaneView;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Invariantes do alocador de lanes (PBT-03) sobre todas as combinacoes de estado. */
class LaneBudgetAllocatorProperties {

  private final LaneBudgetAllocator allocator = new LaneBudgetAllocator();

  @Provide
  Arbitrary<LaneView> views() {
    return Combinators.combine(
            Arbitraries.of(true, false),
            Arbitraries.of(true, false),
            Arbitraries.doubles().between(0.0, 2.0))
        .as(LaneView::new);
  }

  @Property
  void never_picks_a_lane_without_messages(
      @ForAll("views") LaneView fast, @ForAll("views") LaneView standard, @ForAll boolean global) {
    Optional<Lane> pick = allocator.pick(fast, standard, global);

    pick.ifPresent(
        lane ->
            assertThat(lane == Lane.FAST ? fast.hasMessages() : standard.hasMessages()).isTrue());
  }

  @Property
  void never_picks_anything_when_the_global_budget_is_exhausted(
      @ForAll("views") LaneView fast, @ForAll("views") LaneView standard) {
    assertThat(allocator.pick(fast, standard, false)).isEmpty();
  }

  @Property
  void never_lets_a_lane_exceed_its_floor_while_the_other_lane_has_messages(
      @ForAll("views") LaneView fast, @ForAll("views") LaneView standard) {
    Optional<Lane> pick = allocator.pick(fast, standard, true);

    pick.ifPresent(
        lane -> {
          LaneView chosen = lane == Lane.FAST ? fast : standard;
          LaneView other = lane == Lane.FAST ? standard : fast;
          assertThat(chosen.floorReady() || !other.hasMessages()).isTrue();
        });
  }

  @Property
  void always_serves_someone_when_global_is_ready_and_a_lane_is_eligible(
      @ForAll("views") LaneView fast, @ForAll("views") LaneView standard) {
    boolean fastEligible = fast.hasMessages() && (fast.floorReady() || !standard.hasMessages());
    boolean standardEligible =
        standard.hasMessages() && (standard.floorReady() || !fast.hasMessages());

    assertThat(allocator.pick(fast, standard, true).isPresent())
        .isEqualTo(fastEligible || standardEligible);
  }

  @Property
  void lends_the_whole_budget_to_the_only_lane_that_has_messages(
      @ForAll("views") LaneView lane, @ForAll boolean floorReadyOfEmptyLane) {
    LaneView withMessages = new LaneView(true, lane.floorReady(), lane.credit());
    LaneView empty = new LaneView(false, floorReadyOfEmptyLane, 0.0);

    assertThat(allocator.pick(withMessages, empty, true)).contains(Lane.FAST);
    assertThat(allocator.pick(empty, withMessages, true)).contains(Lane.STANDARD);
  }
}
