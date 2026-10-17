package br.com.consignado.platform.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class PlatformDefaultsEnvironmentPostProcessorTest {

  private final PlatformDefaultsEnvironmentPostProcessor processor =
      new PlatformDefaultsEnvironmentPostProcessor();

  @Test
  void should_apply_hardening_and_observability_defaults() {
    StandardEnvironment env = new StandardEnvironment();

    processor.postProcessEnvironment(env, new SpringApplication());

    assertThat(env.getProperty("logging.structured.format.console")).isEqualTo("ecs");
    assertThat(env.getProperty("management.endpoints.web.exposure.include"))
        .isEqualTo("health,info,metrics,prometheus");
    assertThat(env.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
    assertThat(env.getProperty("server.shutdown")).isEqualTo("graceful");
    assertThat(env.getProperty("spring.threads.virtual.enabled")).isEqualTo("true");
    assertThat(env.getProperty("server.error.include-stacktrace")).isEqualTo("never");
    assertThat(env.getProperty("server.error.include-message")).isEqualTo("never");
  }

  @Test
  void should_not_expose_sensitive_actuator_endpoints_by_default() {
    StandardEnvironment env = new StandardEnvironment();

    processor.postProcessEnvironment(env, new SpringApplication());

    assertThat(env.getProperty("management.endpoints.web.exposure.include"))
        .doesNotContain("env")
        .doesNotContain("heapdump")
        .doesNotContain("*");
  }

  @Test
  void should_resolve_placeholders_from_application_name_and_environment() {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources()
        .addFirst(new MapPropertySource("test", Map.of("spring.application.name", "worker")));

    processor.postProcessEnvironment(env, new SpringApplication());

    assertThat(env.getProperty("management.metrics.tags.service")).isEqualTo("worker");
    assertThat(env.getProperty("management.metrics.tags.env")).isEqualTo("local");
    assertThat(env.getProperty("management.tracing.sampling.probability")).isEqualTo("1.0");
  }

  @Test
  void should_keep_the_pushgateway_disabled_by_default() {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources()
        .addFirst(new MapPropertySource("test", Map.of("spring.application.name", "worker")));

    processor.postProcessEnvironment(env, new SpringApplication());

    String pushgateway = "management.prometheus.metrics.export.pushgateway.";
    assertThat(env.getProperty(pushgateway + "enabled")).isEqualTo("false");
    assertThat(env.getProperty(pushgateway + "job")).isEqualTo("worker");
    assertThat(env.getProperty(pushgateway + "grouping-key.instance")).isEqualTo("local");
    assertThat(env.getProperty(pushgateway + "push-rate")).isEqualTo("10s");
    assertThat(env.getProperty(pushgateway + "shutdown-operation")).isEqualTo("put");
  }

  @Test
  void should_enable_the_pushgateway_from_the_environment() {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources()
        .addFirst(
            new MapPropertySource(
                "env",
                Map.of(
                    "PLATFORM_PUSHGATEWAY_ENABLED", "true",
                    "PLATFORM_PUSHGATEWAY_ADDRESS", "203.0.113.10:9091")));

    processor.postProcessEnvironment(env, new SpringApplication());

    String pushgateway = "management.prometheus.metrics.export.pushgateway.";
    assertThat(env.getProperty(pushgateway + "enabled")).isEqualTo("true");
    assertThat(env.getProperty(pushgateway + "address")).isEqualTo("203.0.113.10:9091");
  }

  @Test
  void should_let_the_service_override_any_default() {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources()
        .addFirst(
            new MapPropertySource(
                "override",
                Map.of(
                    "server.shutdown", "immediate",
                    "management.tracing.sampling.probability", "0.1")));

    processor.postProcessEnvironment(env, new SpringApplication());

    assertThat(env.getProperty("server.shutdown")).isEqualTo("immediate");
    assertThat(env.getProperty("management.tracing.sampling.probability")).isEqualTo("0.1");
  }
}
