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
  @ConditionalOnMissingBean
  TenantTagPolicy tenantTagPolicy(TenantMetricsProperties properties) {
    return new TenantTagPolicy(properties.tenantAllowlist(), properties.tenantMax());
  }
}
