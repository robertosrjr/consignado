package br.com.consignado.platform.legacy.config;

import br.com.consignado.platform.legacy.capacity.CapacityGate;
import br.com.consignado.platform.legacy.ledger.AverbacaoLedger;
import java.util.function.LongSupplier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LegacyConfig {

  /** Relogio monotonico; testes registram outro LongSupplier com @Primary. */
  @Bean
  LongSupplier legacyClock() {
    return System::nanoTime;
  }

  @Bean
  CapacityGate capacityGate(LegacyProperties properties, LongSupplier legacyClock) {
    return new CapacityGate(properties.capacityTps(), legacyClock);
  }

  @Bean
  AverbacaoLedger averbacaoLedger(LegacyProperties properties) {
    return new AverbacaoLedger(properties.maxLedgerEntries());
  }

  @Bean
  FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(LegacyProperties properties) {
    FilterRegistrationBean<ApiKeyFilter> registration =
        new FilterRegistrationBean<>(new ApiKeyFilter(properties.apiKey()));
    registration.addUrlPatterns("/v1/*");
    return registration;
  }
}
