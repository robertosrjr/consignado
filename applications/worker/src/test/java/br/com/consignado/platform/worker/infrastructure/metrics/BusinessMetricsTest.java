package br.com.consignado.platform.worker.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.shared.vo.TenantId;
import br.com.consignado.platform.starter.TenantTagPolicy;
import br.com.consignado.platform.worker.application.port.out.ProposalSettlement;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class BusinessMetricsTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final BusinessMetrics metrics =
      new BusinessMetrics(registry, new TenantTagPolicy(List.of("banco-a"), 5));

  private static ProposalSettlement settlement(
      String tenant, Lane lane, boolean confirmed, int attempts, String amount) {
    return new ProposalSettlement(
        new TenantId(tenant),
        lane,
        ConvenioType.INSS,
        false,
        Money.of(amount),
        attempts,
        confirmed,
        Duration.ofMinutes(2));
  }

  @Test
  void should_sum_vgv_and_count_operations_by_outcome_and_lane() {
    metrics.settled(settlement("banco-a", Lane.FAST, true, 1, "1000.00"));
    metrics.settled(settlement("banco-a", Lane.FAST, true, 1, "500.50"));
    metrics.settled(settlement("banco-a", Lane.STANDARD, false, 1, "300.00"));

    // Todas as 24 series existem desde o construtor (contagem zero), entao o seletor precisa de
    // todas as tags; onde o recorte e parcial, soma as series que casam (como no painel).
    var fast =
        registry
            .get("business.vgv")
            .tags(
                "outcome", "confirmed",
                "lane", "fast",
                "convenio", "inss",
                "portability", "false")
            .summary();
    assertThat(fast.count()).isEqualTo(2);
    assertThat(fast.totalAmount()).isEqualTo(1500.50);
    assertThat(countOf("outcome", "rejected")).isEqualTo(1);
    assertThat(countOf("lane", "standard")).isEqualTo(1);
  }

  /** Soma a contagem de todas as series de {@code business.vgv} que casam com a tag informada. */
  private long countOf(String tag, String value) {
    return registry.find("business.vgv").tag(tag, value).summaries().stream()
        .mapToLong(io.micrometer.core.instrument.DistributionSummary::count)
        .sum();
  }

  @Test
  void should_split_first_pass_from_retried_and_only_for_confirmed() {
    metrics.settled(settlement("banco-a", Lane.FAST, true, 1, "10.00"));
    metrics.settled(settlement("banco-a", Lane.FAST, true, 3, "10.00"));
    metrics.settled(settlement("banco-a", Lane.FAST, false, 1, "10.00"));

    assertThat(registry.get("business.first.pass").tag("result", "first_attempt").counter().count())
        .isEqualTo(1);
    assertThat(registry.get("business.first.pass").tag("result", "retried").counter().count())
        .isEqualTo(1);
  }

  @Test
  void should_bound_partner_cardinality_with_the_tenant_policy() {
    metrics.settled(settlement("banco-a", Lane.FAST, true, 1, "10.00"));
    metrics.settled(settlement("robo-1", Lane.FAST, true, 1, "10.00"));
    metrics.settled(settlement("robo-2", Lane.FAST, true, 1, "10.00"));

    assertThat(registry.get("business.partner.settled").tag("tenant", "other").counter().count())
        .isEqualTo(2);
    assertThat(registry.find("business.partner.settled").tag("tenant", "robo-1").counter())
        .isNull();
  }

  @Test
  void should_record_time_to_settle_per_lane() {
    metrics.settled(settlement("banco-a", Lane.FAST, true, 1, "10.00"));

    assertThat(registry.get("business.time.to.settle").tag("lane", "fast").timer().count())
        .isEqualTo(1);
  }
}
