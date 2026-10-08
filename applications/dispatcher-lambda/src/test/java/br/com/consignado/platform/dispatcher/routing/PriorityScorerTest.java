package br.com.consignado.platform.dispatcher.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.shared.vo.TenantId;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PriorityScorerTest {

  private static final PriorityRules RULES =
      PriorityRules.withDefaults(Set.of("vip-bank"), Money.of("50000.00"));
  private static final PriorityScorer SCORER = new PriorityScorer(RULES);
  private static final LaneRouter ROUTER = new LaneRouter(RULES.fastThreshold());

  private static RoutingAttributes attrs(String tenant, boolean portability, String amount) {
    return new RoutingAttributes(
        new TenantId(tenant), ConvenioType.INSS, portability, Money.of(amount));
  }

  @Test
  void should_score_zero_and_go_standard_when_no_criterion_applies() {
    int score = SCORER.score(attrs("banco-a", false, "1000.00"));

    assertThat(score).isZero();
    assertThat(ROUTER.route(score)).isEqualTo(Lane.STANDARD);
  }

  @Test
  void should_send_portability_to_the_fast_lane() {
    int score = SCORER.score(attrs("banco-a", true, "1000.00"));

    assertThat(score).isEqualTo(PriorityRules.DEFAULT_PORTABILITY_WEIGHT);
    assertThat(ROUTER.route(score)).isEqualTo(Lane.FAST);
  }

  @Test
  void should_send_vip_tenants_to_the_fast_lane() {
    assertThat(ROUTER.route(SCORER.score(attrs("vip-bank", false, "1.00")))).isEqualTo(Lane.FAST);
  }

  @Test
  void should_treat_the_amount_threshold_as_inclusive() {
    assertThat(ROUTER.route(SCORER.score(attrs("banco-a", false, "49999.99"))))
        .isEqualTo(Lane.STANDARD);
    assertThat(ROUTER.route(SCORER.score(attrs("banco-a", false, "50000.00"))))
        .isEqualTo(Lane.FAST);
  }

  @Test
  void should_cap_the_score_at_one_hundred() {
    int score = SCORER.score(attrs("vip-bank", true, "99999.00"));

    assertThat(score).isEqualTo(100);
  }

  @Test
  void should_validate_rules() {
    assertThatThrownBy(() -> new PriorityRules(Set.of(), null, 1, 1, 1, 30))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PriorityRules(Set.of(), Money.of("1"), 101, 1, 1, 30))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PriorityRules(Set.of(), Money.of("1"), 1, 1, 1, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
