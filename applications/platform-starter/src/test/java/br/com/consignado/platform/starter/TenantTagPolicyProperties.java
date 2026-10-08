package br.com.consignado.platform.starter;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.testkit.DomainArbitraries;
import br.com.consignado.platform.shared.vo.TenantId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/** Invariante de cardinalidade da tag de tenant (PBT-03): nunca excede allowlist + 1. */
class TenantTagPolicyProperties {

  @Provide
  Arbitrary<TenantId> tenants() {
    return DomainArbitraries.tenantId();
  }

  @Property
  void resolved_values_never_exceed_the_declared_cardinality(
      @ForAll @Size(max = 80) List<@From("tenants") TenantId> configured,
      @ForAll @IntRange(min = 0, max = 60) int max,
      @ForAll @Size(max = 300) List<@From("tenants") TenantId> traffic) {
    TenantTagPolicy policy =
        new TenantTagPolicy(configured.stream().map(TenantId::value).toList(), max);

    Set<String> observed = new HashSet<>();
    for (TenantId tenant : traffic) {
      observed.add(policy.resolve(tenant.value()));
    }

    assertThat(observed.size()).isLessThanOrEqualTo(policy.maxCardinality());
    assertThat(policy.maxCardinality()).isLessThanOrEqualTo(max + 1);
  }

  @Property
  void an_unconfigured_tenant_is_always_aggregated(
      @ForAll("tenants") TenantId configured, @ForAll("tenants") TenantId other) {
    TenantTagPolicy policy = new TenantTagPolicy(List.of(configured.value()), 50);

    if (!configured.equals(other)) {
      assertThat(policy.resolve(other.value())).isEqualTo(TenantTagPolicy.OTHER);
    }
    assertThat(policy.resolve(configured.value())).isEqualTo(configured.value());
  }
}
