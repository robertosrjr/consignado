package br.com.consignado.platform.ingestion.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Propriedades {@code ingestion.*} (variaveis INGESTION_*). Segredos nao tem valor padrao. */
@ConfigurationProperties("ingestion")
@Validated
public record IngestionProperties(
    @Valid @DefaultValue Inbox inbox,
    @Valid @DefaultValue Quota quota,
    @Valid @DefaultValue Payload payload,
    @Valid @NotNull Jwt jwt,
    @DefaultValue("16384") @Min(1024) int maxBodyBytes) {

  /** DurableInbox: {@code dynamodb} (padrao) ou {@code memory} (somente desenvolvimento/testes). */
  public record Inbox(
      @DefaultValue("dynamodb") String type,
      @DefaultValue("DurableInbox") @NotBlank String tableName,
      @DefaultValue("P7D") Duration retention,
      @DefaultValue("500ms") Duration apiCallTimeout,
      @DefaultValue("150ms") Duration apiCallAttemptTimeout,
      @DefaultValue("200") @Min(1) int maxConnections,
      @DefaultValue("60s") Duration recentKeysTtl,
      @DefaultValue("100000") @Min(0) int recentKeysMaxEntries) {}

  /** Cotas por tenant (permissoes por segundo), locais a esta instancia. */
  public record Quota(
      @DefaultValue("100") @Min(1) int defaultPerSecond,
      @DefaultValue Map<String, Integer> tenants,
      @DefaultValue("10000") @Min(1) int maxTenants) {}

  /**
   * Protecao do payload: {@code local} (AES-GCM, chave por variavel de ambiente) ou {@code kms}.
   */
  public record Payload(
      @DefaultValue("local") String mode,
      String localKey,
      String kmsKeyId,
      @DefaultValue("10000") @Min(1) int kmsDataKeyMaxUses,
      @DefaultValue("PT5M") Duration kmsDataKeyMaxAge) {}

  /** Validacao do JWT (assinatura via JWKS, emissor, audiencia) e claim do tenant. */
  public record Jwt(
      @NotBlank String jwkSetUri,
      String issuer,
      String audience,
      @DefaultValue("tenant") String tenantClaim) {}
}
