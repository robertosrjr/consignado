package br.com.consignado.platform.ingestion.infrastructure.adapter.in.web;

import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalCommand;
import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalResult;
import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalUseCase;
import br.com.consignado.platform.ingestion.domain.IngestionOutcome;
import br.com.consignado.platform.ingestion.domain.TenantQuotaExceededException;
import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.starter.TenantTagPolicy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decorador de metricas do caso de uso: {@code ingestion.accepted} por resultado, {@code
 * ingestion.throttled.tenant} e o VGV aceito na borda ({@code business.vgv.received}), com tag de
 * tenant sob a politica de cardinalidade (allowlist).
 *
 * <p>Os medidores sao registrados no construtor (combinacoes fixas de convenio e portabilidade) ou
 * resolvidos uma vez por tenant: o caminho de 10.000 req/s nao reconstroi medidor por requisicao.
 */
public final class MeteredAcceptProposalUseCase implements AcceptProposalUseCase {

  private final AcceptProposalUseCase delegate;
  private final TenantTagPolicy tenantTags;
  private final Map<IngestionOutcome, Counter> accepted = new EnumMap<>(IngestionOutcome.class);
  private final Map<ConvenioType, DistributionSummary[]> vgvReceived =
      new EnumMap<>(ConvenioType.class);
  private final Map<String, Counter> partnerReceived = new ConcurrentHashMap<>();
  private final Map<String, Counter> throttled = new ConcurrentHashMap<>();
  private final MeterRegistry registry;

  public MeteredAcceptProposalUseCase(
      AcceptProposalUseCase delegate, MeterRegistry registry, TenantTagPolicy tenantTags) {
    this.delegate = delegate;
    this.registry = registry;
    this.tenantTags = tenantTags;
    for (IngestionOutcome outcome : IngestionOutcome.values()) {
      accepted.put(
          outcome, registry.counter("ingestion.accepted", "outcome", lower(outcome.name())));
    }
    for (ConvenioType convenio : ConvenioType.values()) {
      DistributionSummary[] byPortability = new DistributionSummary[2];
      for (int portability = 0; portability < 2; portability++) {
        byPortability[portability] =
            DistributionSummary.builder("business.vgv.received")
                .description("Valor (R$) e quantidade de propostas aceitas na borda (202 novas)")
                .baseUnit("brl")
                .tags(
                    "convenio",
                    lower(convenio.name()),
                    "portability",
                    Boolean.toString(portability == 1))
                .register(registry);
      }
      vgvReceived.put(convenio, byPortability);
    }
  }

  @Override
  public AcceptProposalResult accept(AcceptProposalCommand command) {
    try {
      AcceptProposalResult result = delegate.accept(command);
      accepted.get(result.outcome()).increment();
      if (result.outcome() == IngestionOutcome.DUPLICATE_ACCEPTED) {
        registry
            .counter(
                "ingestion.deduplicated",
                "kind",
                result.semanticDuplicate() ? "semantic" : "technical")
            .increment();
      }
      if (result.outcome() == IngestionOutcome.RECEIVED) {
        recordBusiness(command);
      }
      return result;
    } catch (br.com.consignado.platform.ingestion.domain.IdempotencyMismatchException mismatch) {
      registry.counter("ingestion.idempotency.mismatch").increment();
      throw mismatch;
    } catch (TenantQuotaExceededException quotaExceeded) {
      counterByTenant(throttled, "ingestion.throttled.tenant", command).increment();
      throw quotaExceeded;
    }
  }

  /** Somente propostas novas (RECEIVED): duplicatas nao inflam o VGV preservado. */
  private void recordBusiness(AcceptProposalCommand command) {
    vgvReceived.get(command.convenio())[command.portability() ? 1 : 0].record(
        command.amount().amount().doubleValue());
    counterByTenant(partnerReceived, "business.partner.received", command).increment();
  }

  private Counter counterByTenant(
      Map<String, Counter> cache, String name, AcceptProposalCommand command) {
    String tenant = tenantTags.resolve(command.tenant().value());
    return cache.computeIfAbsent(tenant, tag -> registry.counter(name, "tenant", tag));
  }

  private static String lower(String value) {
    return value.toLowerCase(Locale.ROOT);
  }
}
