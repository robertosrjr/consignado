package br.com.consignado.platform.legacy.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.legacy.config.LegacyFaultPolicy;
import br.com.consignado.platform.legacy.config.LegacyProperties;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class FaultControlIsolationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(ControlConfiguration.class)
          .withPropertyValues("legacy.api-key=test-key");

  @Test
  void requires_both_the_profile_and_the_explicit_flag() {
    runner
        .withPropertyValues("legacy.fault-control.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(FaultControlController.class));
    runner
        .withPropertyValues("spring.profiles.active=sre-experiment")
        .run(context -> assertThat(context).doesNotHaveBean(FaultControlController.class));
  }

  @Test
  void fails_closed_for_a_missing_or_short_token_and_non_neutral_baseline() {
    var enabled =
        runner.withPropertyValues(
            "spring.profiles.active=sre-experiment", "legacy.fault-control.enabled=true");
    enabled.run(context -> assertThat(context).hasFailed());
    enabled
        .withPropertyValues("legacy.fault-control.token=short")
        .run(context -> assertThat(context).hasFailed());
    enabled
        .withPropertyValues(
            "legacy.fault-control.token=" + FaultControlTest.TOKEN, "legacy.chaos.error-rate=1")
        .run(context -> assertThat(context).hasFailed());
    enabled
        .withPropertyValues(
            "legacy.fault-control.token=" + FaultControlTest.TOKEN, "legacy.chaos.latency-ms=1")
        .run(context -> assertThat(context).hasFailed());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(LegacyProperties.class)
  @Import({LegacyFaultPolicy.class, FaultControlController.class})
  static class ControlConfiguration {
    @Bean
    LongSupplier clock() {
      return System::nanoTime;
    }
  }
}
