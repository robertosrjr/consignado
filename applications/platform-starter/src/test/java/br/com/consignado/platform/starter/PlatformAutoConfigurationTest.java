package br.com.consignado.platform.starter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlatformAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PlatformAutoConfiguration.class));

  @Test
  void should_bind_allowlist_and_maximum_from_properties() {
    runner
        .withPropertyValues(
            "platform.metrics.tenant-allowlist=banco-a,banco-b,banco-c",
            "platform.metrics.tenant-max=2")
        .run(
            context -> {
              TenantTagPolicy policy = context.getBean(TenantTagPolicy.class);
              assertThat(policy.resolve("banco-a")).isEqualTo("banco-a");
              assertThat(policy.resolve("banco-b")).isEqualTo("banco-b");
              assertThat(policy.resolve("banco-c")).isEqualTo(TenantTagPolicy.OTHER);
            });
  }

  @Test
  void should_default_to_empty_allowlist_and_aggregate_all_tenants() {
    runner.run(
        context ->
            assertThat(context.getBean(TenantTagPolicy.class).resolve("banco-a"))
                .isEqualTo(TenantTagPolicy.OTHER));
  }

  @Test
  void should_fail_startup_when_allowlist_contains_an_invalid_tenant() {
    runner
        .withPropertyValues("platform.metrics.tenant-allowlist=valid,not valid")
        .run(context -> assertThat(context).hasFailed());
  }
}
