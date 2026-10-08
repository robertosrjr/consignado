package br.com.consignado.platform.legacy.config;

import br.com.consignado.platform.legacy.capacity.CapacityGate;
import br.com.consignado.platform.legacy.ledger.AverbacaoLedger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Metricas do legado simulado (FR-5.2): vazao real na janela de 1 s, pico de janela, latencia e
 * contadores de averbacoes unicas e duplicadas (evidencia do DoD de idempotencia e do teto de 180
 * TPS).
 */
@Component
public class LegacyMetrics {

  private final Counter accepted;
  private final Counter rejectedCapacity;
  private final Counter failedChaos;
  private final Counter unique;
  private final Counter duplicate;
  private final Timer latency;

  public LegacyMetrics(MeterRegistry registry, CapacityGate gate, AverbacaoLedger ledger) {
    Gauge.builder("legacy.request.throughput", gate, CapacityGate::current)
        .description("Requisicoes aceitas na janela atual de 1 s")
        .register(registry);
    Gauge.builder("legacy.peak.window", gate, CapacityGate::peak)
        .description("Maior numero de requisicoes aceitas em uma janela de 1 s")
        .register(registry);
    Gauge.builder("legacy.ledger.size", ledger, AverbacaoLedger::size).register(registry);
    this.accepted = registry.counter("legacy.requests", "outcome", "accepted");
    this.rejectedCapacity = registry.counter("legacy.requests", "outcome", "rejected_capacity");
    this.failedChaos = registry.counter("legacy.requests", "outcome", "failed_chaos");
    this.unique = registry.counter("legacy.averbacoes", "kind", "unique");
    this.duplicate = registry.counter("legacy.averbacoes", "kind", "duplicate");
    this.latency =
        Timer.builder("legacy.request.latency")
            .description("Latencia do processamento no legado simulado")
            .publishPercentileHistogram()
            .register(registry);
  }

  public Timer latency() {
    return latency;
  }

  public void accepted() {
    accepted.increment();
  }

  public void rejectedCapacity() {
    rejectedCapacity.increment();
  }

  public void failedChaos() {
    failedChaos.increment();
  }

  public void averbacao(boolean isDuplicate) {
    (isDuplicate ? duplicate : unique).increment();
  }
}
