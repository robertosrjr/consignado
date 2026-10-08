package br.com.consignado.platform.worker.infrastructure.metrics;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.starter.TenantTagPolicy;
import br.com.consignado.platform.worker.application.port.out.BusinessObserver;
import br.com.consignado.platform.worker.application.port.out.ProposalSettlement;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Metricas de negocio do Worker. {@code business.vgv} (soma = R$, contagem = operacoes) por
 * resultado, convenio, lane e portabilidade; {@code business.partner.settled} por parceiro (sob a
 * politica de cardinalidade); {@code business.first.pass} para a averbacao de primeira passagem;
 * {@code business.time.to.settle} com limites de SLA (inclui os 5 min da virada).
 *
 * <p>Cardinalidade fixa: 24 series em {@code business.vgv} (2 resultados x 3 convenios x 2 lanes x
 * 2 portabilidades). Todos os medidores de combinacao fixa sao registrados no construtor; os de
 * parceiro sao resolvidos uma vez por tenant. Nenhuma tag carrega dado pessoal.
 *
 * <p>A contagem reflete somente este processo (os medidores sao locais a JVM); o total do painel
 * vem da soma das instancias no Prometheus.
 */
public final class BusinessMetrics implements BusinessObserver {

  private record VgvKey(boolean confirmed, ConvenioType convenio, Lane lane, boolean portability) {}

  private final TenantTagPolicy tenantTags;
  private final MeterRegistry registry;
  private final Map<VgvKey, DistributionSummary> vgv = new HashMap<>();
  private final Map<Lane, Timer> timeToSettle = new EnumMap<>(Lane.class);
  private final Counter firstAttempt;
  private final Counter retried;
  private final Map<String, Counter> partnerSettled = new ConcurrentHashMap<>();

  public BusinessMetrics(MeterRegistry registry, TenantTagPolicy tenantTags) {
    this.registry = registry;
    this.tenantTags = tenantTags;
    for (boolean confirmed : new boolean[] {true, false}) {
      for (ConvenioType convenio : ConvenioType.values()) {
        for (Lane lane : Lane.values()) {
          for (boolean portability : new boolean[] {true, false}) {
            VgvKey key = new VgvKey(confirmed, convenio, lane, portability);
            vgv.put(key, registerVgv(key));
          }
        }
      }
    }
    for (Lane lane : Lane.values()) {
      timeToSettle.put(
          lane,
          Timer.builder("business.time.to.settle")
              .description("Tempo do aceite na borda ate o estado final no legado")
              .serviceLevelObjectives(
                  Duration.ofSeconds(30),
                  Duration.ofMinutes(1),
                  Duration.ofMinutes(5),
                  Duration.ofMinutes(15),
                  Duration.ofMinutes(30))
              .tag("lane", lower(lane.name()))
              .register(registry));
    }
    this.firstAttempt = registry.counter("business.first.pass", "result", "first_attempt");
    this.retried = registry.counter("business.first.pass", "result", "retried");
  }

  @Override
  public void settled(ProposalSettlement settlement) {
    vgv.get(
            new VgvKey(
                settlement.confirmed(),
                settlement.convenio(),
                settlement.lane(),
                settlement.portability()))
        .record(settlement.amount().amount().doubleValue());
    partnerCounter(settlement).increment();
    if (settlement.confirmed()) {
      if (settlement.attempts() <= 1) {
        firstAttempt.increment();
      } else {
        retried.increment();
      }
    }
    Duration elapsed = settlement.timeToSettle();
    if (elapsed != null && !elapsed.isNegative()) {
      timeToSettle.get(settlement.lane()).record(elapsed);
    }
  }

  private DistributionSummary registerVgv(VgvKey key) {
    return DistributionSummary.builder("business.vgv")
        .description("Valor (R$) e quantidade de propostas finalizadas no legado")
        .baseUnit("brl")
        .tags(
            "outcome",
            key.confirmed() ? "confirmed" : "rejected",
            "convenio",
            lower(key.convenio().name()),
            "lane",
            lower(key.lane().name()),
            "portability",
            Boolean.toString(key.portability()))
        .register(registry);
  }

  private Counter partnerCounter(ProposalSettlement settlement) {
    String tenant = tenantTags.resolve(settlement.tenant().value());
    String outcome = settlement.confirmed() ? "confirmed" : "rejected";
    return partnerSettled.computeIfAbsent(
        tenant + '|' + outcome,
        ignored ->
            registry.counter("business.partner.settled", "tenant", tenant, "outcome", outcome));
  }

  private static String lower(String value) {
    return value.toLowerCase(Locale.ROOT);
  }
}
