package br.com.consignado.platform.ingestion.infrastructure.adapter.out.quota;

import br.com.consignado.platform.ingestion.application.port.out.TenantQuotaPort;
import br.com.consignado.platform.shared.vo.TenantId;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cota por tenant com um limitador por parceiro (permissoes por segundo, sem espera). A protecao e
 * <b>local ao processo</b>: com varias instancias a cota efetiva e multiplicada; a camada
 * distribuida e o WAF. O numero de limitadores e limitado: acima de {@code maxTenants} tenants
 * distintos, os desconhecidos compartilham um limitador de overflow (protege a memoria contra
 * {@code X-Tenant-ID} aleatorio).
 */
public final class Resilience4jTenantQuotaAdapter implements TenantQuotaPort {

  private static final String OVERFLOW = "\u0000overflow";

  private final int defaultPerSecond;
  private final Map<String, Integer> overrides;
  private final int maxTenants;
  private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

  public Resilience4jTenantQuotaAdapter(
      int defaultPerSecond, Map<String, Integer> overrides, int maxTenants) {
    if (defaultPerSecond < 1 || maxTenants < 1) {
      throw new IllegalArgumentException("Cota invalida");
    }
    this.defaultPerSecond = defaultPerSecond;
    this.overrides = Map.copyOf(overrides);
    this.maxTenants = maxTenants;
  }

  @Override
  public boolean tryAcquire(TenantId tenant) {
    return limiterFor(tenant.value()).acquirePermission();
  }

  private RateLimiter limiterFor(String tenant) {
    RateLimiter existing = limiters.get(tenant);
    if (existing != null) {
      return existing;
    }
    boolean configured = overrides.containsKey(tenant);
    if (!configured && limiters.size() >= maxTenants) {
      return limiters.computeIfAbsent(OVERFLOW, key -> create(key, defaultPerSecond));
    }
    return limiters.computeIfAbsent(tenant, key -> create(key, quotaOf(key)));
  }

  private int quotaOf(String tenant) {
    return overrides.getOrDefault(tenant, defaultPerSecond);
  }

  private static RateLimiter create(String name, int perSecond) {
    return RateLimiter.of(
        "tenant-" + name,
        RateLimiterConfig.custom()
            .limitForPeriod(perSecond)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ZERO)
            .build());
  }

  /** Numero de limitadores ativos (para testes e diagnostico). */
  public int activeLimiters() {
    return limiters.size();
  }
}
