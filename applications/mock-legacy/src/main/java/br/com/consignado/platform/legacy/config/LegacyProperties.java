package br.com.consignado.platform.legacy.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Propriedades {@code legacy.*} (variaveis LEGACY_*). {@code api-key} nao tem valor padrao. */
@ConfigurationProperties("legacy")
@Validated
public record LegacyProperties(
    @NotBlank String apiKey,
    @DefaultValue("180") @Min(1) int capacityTps,
    @DefaultValue("1000000") @Min(1000) int maxLedgerEntries,
    @Valid @DefaultValue Chaos chaos) {

  /** Falhas simuladas para testar circuit breaker e timeouts do Worker. */
  public record Chaos(
      @DefaultValue("0") @Min(0) long latencyMs,
      @DefaultValue("0.0") @DecimalMin("0.0") @DecimalMax("1.0") double errorRate) {}
}
