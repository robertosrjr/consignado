package br.com.consignado.platform.starter;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Propriedades {@code platform.metrics.*} (variaveis PLATFORM_METRICS_TENANT_*). */
@ConfigurationProperties("platform.metrics")
public record TenantMetricsProperties(
    @DefaultValue List<String> tenantAllowlist, @DefaultValue("50") int tenantMax) {}
