package br.com.consignado.platform.dispatcher.routing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.testkit.DomainArbitraries;
import br.com.consignado.platform.shared.vo.Money;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Propriedades da regra de prioridade (PBT-03): limites, monotonicidade e equivalencia com a regra.
 */
class PriorityScorerProperties {

  private static final Money THRESHOLD = Money.of("50000.00");
  private static final PriorityRules RULES =
      PriorityRules.withDefaults(Set.of("vip-a", "vip-b"), THRESHOLD);
  private static final PriorityScorer SCORER = new PriorityScorer(RULES);
  private static final LaneRouter ROUTER = new LaneRouter(RULES.fastThreshold());

  @Provide
  Arbitrary<RoutingAttributes> attributes() {
    Arbitrary<br.com.consignado.platform.shared.vo.TenantId> tenants =
        Arbitraries.oneOf(
            Arbitraries.of("vip-a", "vip-b")
                .map(br.com.consignado.platform.shared.vo.TenantId::new),
            DomainArbitraries.tenantId());
    return Combinators.combine(
            tenants,
            Arbitraries.of(ConvenioType.class),
            Arbitraries.of(true, false),
            DomainArbitraries.money())
        .as(RoutingAttributes::new);
  }

  @Property
  void score_is_always_between_zero_and_one_hundred(@ForAll("attributes") RoutingAttributes a) {
    assertThat(SCORER.score(a)).isBetween(0, 100);
  }

  @Property
  void adding_portability_never_lowers_the_score(@ForAll("attributes") RoutingAttributes a) {
    RoutingAttributes without = new RoutingAttributes(a.tenant(), a.convenio(), false, a.amount());
    RoutingAttributes with = new RoutingAttributes(a.tenant(), a.convenio(), true, a.amount());

    assertThat(SCORER.score(with)).isGreaterThanOrEqualTo(SCORER.score(without));
  }

  @Property
  void fast_lane_is_chosen_exactly_when_any_criterion_applies(
      @ForAll("attributes") RoutingAttributes a) {
    boolean anyCriterion =
        a.portability()
            || RULES.vipTenants().contains(a.tenant().value())
            || !THRESHOLD.isGreaterThan(a.amount());

    Lane lane = ROUTER.route(SCORER.score(a));

    assertThat(lane == Lane.FAST).isEqualTo(anyCriterion);
  }

  @Property
  void scoring_is_deterministic(@ForAll("attributes") RoutingAttributes a) {
    assertThat(SCORER.score(a)).isEqualTo(SCORER.score(a));
  }

  @Property
  void router_is_monotonic_in_the_score(
      @ForAll @IntRange(min = 0, max = 100) int low,
      @ForAll @IntRange(min = 0, max = 100) int high) {
    int s1 = Math.min(low, high);
    int s2 = Math.max(low, high);

    if (ROUTER.route(s1) == Lane.FAST) {
      assertThat(ROUTER.route(s2)).isEqualTo(Lane.FAST);
    }
  }
}
