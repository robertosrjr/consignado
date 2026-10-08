package br.com.consignado.platform.ingestion.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.ingestion.infrastructure.adapter.out.quota.Resilience4jTenantQuotaAdapter;
import br.com.consignado.platform.shared.vo.TenantId;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Resilience4jTenantQuotaAdapterTest {

  @Test
  void should_isolate_tenants_so_one_exhausting_its_quota_does_not_affect_the_others() {
    Resilience4jTenantQuotaAdapter quota =
        new Resilience4jTenantQuotaAdapter(1000, Map.of("anomalo", 2), 100);
    TenantId anomalo = new TenantId("anomalo");
    TenantId normal = new TenantId("normal");

    int accepted = 0;
    for (int i = 0; i < 50; i++) {
      if (quota.tryAcquire(anomalo)) {
        accepted++;
      }
    }

    assertThat(accepted).isBetween(2, 4);
    assertThat(quota.tryAcquire(normal)).isTrue();
  }

  @Test
  void should_apply_the_default_quota_to_tenants_without_override() {
    Resilience4jTenantQuotaAdapter quota = new Resilience4jTenantQuotaAdapter(3, Map.of(), 100);
    TenantId tenant = new TenantId("parceiro");

    long accepted =
        java.util.stream.IntStream.range(0, 20).filter(i -> quota.tryAcquire(tenant)).count();

    assertThat(accepted).isBetween(3L, 6L);
  }

  @Test
  void should_bound_the_number_of_limiters_against_random_tenant_ids() {
    Resilience4jTenantQuotaAdapter quota = new Resilience4jTenantQuotaAdapter(10, Map.of(), 5);

    for (int i = 0; i < 1000; i++) {
      quota.tryAcquire(new TenantId("t" + i));
    }

    assertThat(quota.activeLimiters()).isLessThanOrEqualTo(6);
  }

  @Test
  void should_share_the_overflow_limiter_among_unknown_tenants_beyond_the_limit() {
    Resilience4jTenantQuotaAdapter quota = new Resilience4jTenantQuotaAdapter(2, Map.of(), 1);
    quota.tryAcquire(new TenantId("primeiro"));

    long accepted =
        java.util.stream.IntStream.range(0, 40)
            .filter(i -> quota.tryAcquire(new TenantId("novo" + i)))
            .count();

    assertThat(accepted).isLessThanOrEqualTo(4);
  }

  @Test
  void should_reject_invalid_configuration() {
    assertThatThrownBy(() -> new Resilience4jTenantQuotaAdapter(0, Map.of(), 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Resilience4jTenantQuotaAdapter(10, Map.of(), 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
