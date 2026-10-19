package br.com.consignado.platform.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Beans comuns de observabilidade (independentes de web). */
@AutoConfiguration
@EnableConfigurationProperties(TenantMetricsProperties.class)
public class PlatformAutoConfiguration {
  @Bean
  org.springframework.beans.factory.InitializingBean configurePlatformTracing(
      org.springframework.beans.factory.ObjectProvider<io.opentelemetry.api.OpenTelemetry>
          telemetry) {
    return () ->
        br.com.consignado.platform.telemetry.Telemetry.configure(
            telemetry.getIfAvailable(io.opentelemetry.api.OpenTelemetry::noop));
  }

  @Bean
  @ConditionalOnMissingBean
  TenantTagPolicy tenantTagPolicy(TenantMetricsProperties properties) {
    return new TenantTagPolicy(properties.tenantAllowlist(), properties.tenantMax());
  }
}
