package br.com.consignado.platform.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TenantTagPolicyTest {

  @Test
  void should_return_tenant_when_it_is_in_the_allowlist() {
    TenantTagPolicy policy = new TenantTagPolicy(List.of("banco-a", "banco-b"), 50);

    assertThat(policy.resolve("banco-a")).isEqualTo("banco-a");
  }

  @Test
  void should_aggregate_unknown_and_null_tenants_into_other() {
    TenantTagPolicy policy = new TenantTagPolicy(List.of("banco-a"), 50);

    assertThat(policy.resolve("random-123")).isEqualTo(TenantTagPolicy.OTHER);
    assertThat(policy.resolve(null)).isEqualTo(TenantTagPolicy.OTHER);
  }

  @Test
  void should_limit_the_allowlist_to_the_configured_maximum() {
    TenantTagPolicy policy = new TenantTagPolicy(List.of("a", "b", "c"), 2);

    assertThat(policy.resolve("a")).isEqualTo("a");
    assertThat(policy.resolve("b")).isEqualTo("b");
    assertThat(policy.resolve("c")).isEqualTo(TenantTagPolicy.OTHER);
    assertThat(policy.maxCardinality()).isEqualTo(3);
  }

  @Test
  void should_fail_fast_on_invalid_tenant_in_the_allowlist() {
    assertThatThrownBy(() -> new TenantTagPolicy(List.of("ok", "bad tenant"), 50))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_negative_maximum() {
    assertThatThrownBy(() -> new TenantTagPolicy(List.of(), -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_aggregate_everything_when_allowlist_is_empty() {
    TenantTagPolicy policy = new TenantTagPolicy(List.of(), 50);

    assertThat(policy.resolve("banco-a")).isEqualTo(TenantTagPolicy.OTHER);
    assertThat(policy.maxCardinality()).isEqualTo(1);
  }
}
