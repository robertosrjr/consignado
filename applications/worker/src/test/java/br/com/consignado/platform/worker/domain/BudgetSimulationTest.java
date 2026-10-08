package br.com.consignado.platform.worker.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.worker.application.port.out.BudgetSnapshot;
import br.com.consignado.platform.worker.domain.LaneBudgetAllocator.LaneView;
import br.com.consignado.platform.worker.infrastructure.adapter.out.budget.FixedPerTaskBudgetAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Simulacao deterministica (relogio simulado, passo de 100 microssegundos) do orcamento completo:
 * evidencia dos criterios do DoD sobre o teto do legado (<= 180 TPS em qualquer janela de 1 s) e a
 * proporcao 70/30 com emprestimo de capacidade.
 */
class BudgetSimulationTest {

  private static final long SECOND = 1_000_000_000L;
  private static final long STEP = 100_000L;

  private record Grant(long at, Lane lane) {}

  /**
   * Executa a simulacao; {@code fastBacklog} e {@code standardBacklog} sao os estoques de
   * mensagens.
   */
  private static List<Grant> simulate(
      long durationSeconds, long fastBacklog, long standardBacklog, int instances) {
    long[] clock = {0L};
    FixedPerTaskBudgetAdapter budget =
        new FixedPerTaskBudgetAdapter(180, instances, 0.70, 0.98, 2, () -> clock[0]);
    LaneBudgetAllocator allocator = new LaneBudgetAllocator();
    long[] remaining = {fastBacklog, standardBacklog};
    List<Grant> grants = new ArrayList<>();

    for (long t = 0; t < durationSeconds * SECOND; t += STEP) {
      clock[0] = t;
      BudgetSnapshot snapshot = budget.snapshot();
      Optional<Lane> pick =
          allocator.pick(
              new LaneView(
                  remaining[0] > 0, snapshot.fast().floorReady(), snapshot.fast().credit()),
              new LaneView(
                  remaining[1] > 0, snapshot.standard().floorReady(), snapshot.standard().credit()),
              snapshot.globalReady());
      if (pick.isPresent()) {
        budget.consume(pick.get());
        remaining[pick.get() == Lane.FAST ? 0 : 1]--;
        grants.add(new Grant(t, pick.get()));
      }
    }
    return grants;
  }

  private static long maxInAnyOneSecondWindow(List<Grant> grants) {
    long max = 0;
    int start = 0;
    for (int end = 0; end < grants.size(); end++) {
      while (grants.get(end).at() - grants.get(start).at() >= SECOND) {
        start++;
      }
      max = Math.max(max, end - start + 1);
    }
    return max;
  }

  private static long count(List<Grant> grants, Lane lane) {
    return grants.stream().filter(g -> g.lane() == lane).count();
  }

  @Test
  void should_never_exceed_the_legacy_ceiling_in_any_one_second_window() {
    List<Grant> grants = simulate(20, 1_000_000, 1_000_000, 1);

    assertThat(maxInAnyOneSecondWindow(grants)).isLessThanOrEqualTo(180);
  }

  @Test
  void should_use_almost_the_whole_budget_when_there_is_backlog() {
    List<Grant> grants = simulate(20, 1_000_000, 1_000_000, 1);

    double perSecond = grants.size() / 20.0;
    assertThat(perSecond).isBetween(170.0, 180.0);
  }

  @Test
  void should_split_the_budget_70_30_when_both_lanes_have_backlog() {
    List<Grant> grants = simulate(30, 1_000_000, 1_000_000, 1);

    double fastShare = (double) count(grants, Lane.FAST) / grants.size();
    assertThat(fastShare).isBetween(0.68, 0.72);
    assertThat(count(grants, Lane.STANDARD)).isGreaterThan(0);
  }

  @Test
  void should_never_starve_the_standard_lane() {
    List<Grant> grants = simulate(10, 1_000_000, 1_000_000, 1);

    long standardPerSecond = count(grants, Lane.STANDARD) / 10;
    assertThat(standardPerSecond).isGreaterThanOrEqualTo(50);
  }

  @Test
  void should_lend_the_idle_standard_budget_to_fast() {
    List<Grant> grants = simulate(20, 1_000_000, 0, 1);

    assertThat(count(grants, Lane.STANDARD)).isZero();
    assertThat(grants.size() / 20.0).isBetween(170.0, 180.0);
    assertThat(maxInAnyOneSecondWindow(grants)).isLessThanOrEqualTo(180);
  }

  @Test
  void should_lend_the_idle_fast_budget_to_standard() {
    List<Grant> grants = simulate(20, 0, 1_000_000, 1);

    assertThat(count(grants, Lane.FAST)).isZero();
    assertThat(grants.size() / 20.0).isBetween(170.0, 180.0);
    assertThat(maxInAnyOneSecondWindow(grants)).isLessThanOrEqualTo(180);
  }

  @Test
  void should_divide_the_ceiling_among_instances() {
    List<Grant> grants = simulate(20, 1_000_000, 1_000_000, 3);

    assertThat(grants.size() / 20.0).isBetween(55.0, 60.0);
  }

  @Test
  void should_drain_a_finite_backlog_in_priority_proportion_until_both_are_empty() {
    List<Grant> grants = simulate(60, 7_000, 3_000, 1);

    assertThat(count(grants, Lane.FAST)).isEqualTo(7_000);
    assertThat(count(grants, Lane.STANDARD)).isEqualTo(3_000);
    assertThat(maxInAnyOneSecondWindow(grants)).isLessThanOrEqualTo(180);
  }
}
