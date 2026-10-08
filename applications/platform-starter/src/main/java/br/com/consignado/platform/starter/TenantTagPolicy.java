package br.com.consignado.platform.starter;

import br.com.consignado.platform.shared.vo.TenantId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Politica de cardinalidade da tag {@code tenant} em metricas: somente tenants da allowlist
 * configurada (limitada a {@code max}) recebem tag propria; os demais agregam em {@code other}.
 * Protege o Prometheus contra um robo que envie {@code X-Tenant-ID} aleatorio.
 */
public final class TenantTagPolicy {

  public static final String OTHER = "other";

  private final Set<String> allowlist;

  public TenantTagPolicy(Collection<String> configuredTenants, int max) {
    if (max < 0) {
      throw new IllegalArgumentException("platform.metrics.tenant-max invalido");
    }
    Set<String> accepted = new LinkedHashSet<>();
    for (String tenant : configuredTenants) {
      if (accepted.size() >= max) {
        break;
      }
      accepted.add(new TenantId(tenant.trim()).value());
    }
    this.allowlist = Set.copyOf(accepted);
  }

  /** Valor da tag {@code tenant} para o tenant informado (nunca nulo). */
  public String resolve(String tenantId) {
    if (tenantId == null) {
      return OTHER;
    }
    return allowlist.contains(tenantId) ? tenantId : OTHER;
  }

  /** Cardinalidade maxima da tag: tamanho da allowlist mais o valor {@code other}. */
  public int maxCardinality() {
    return allowlist.size() + 1;
  }
}
