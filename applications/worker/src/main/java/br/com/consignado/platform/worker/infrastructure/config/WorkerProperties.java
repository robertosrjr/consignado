package br.com.consignado.platform.worker.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Propriedades {@code worker.*} (variaveis WORKER_*). Segredos nao tem valor padrao. */
@ConfigurationProperties("worker")
@Validated
public record WorkerProperties(
    @Valid @DefaultValue Budget budget,
    @Valid @NotNull Legacy legacy,
    @Valid @DefaultValue Queue queue,
    @Valid @DefaultValue Inbox inbox,
    @Valid @DefaultValue Payload payload,
    @DefaultValue("360") @Min(1) int maxInFlight,
    @DefaultValue("5") @Min(1) int metricsSamplePeriodSeconds) {

  /** Orcamento de vazao ate o legado (180 TPS dividido pelas instancias, com margem). */
  public record Budget(
      @DefaultValue("180") @DecimalMin("1") double totalTps,
      @DefaultValue("1") @Min(1) int instances,
      @DefaultValue("0.70") @DecimalMin("0.01") @DecimalMax("0.99") double fastShare,
      @DefaultValue("0.98") @DecimalMin("0.01") @DecimalMax("1.0") double safetyMargin,
      @DefaultValue("2") @DecimalMin("1") double bucketCapacity) {}

  /** Cliente do legado e circuit breaker. */
  public record Legacy(
      @NotBlank String baseUrl,
      @NotBlank String apiKey,
      @DefaultValue("3s") Duration requestTimeout,
      @DefaultValue("2s") Duration slowCallThreshold,
      @DefaultValue("10") @Min(1) int failureRatePercent,
      @DefaultValue("20") @Min(1) int minimumCalls,
      @DefaultValue("100") @Min(1) int windowSize,
      @DefaultValue("10s") Duration openStateWait) {}

  /** Filas das lanes: {@code sqs} (padrao) ou {@code memory} (somente desenvolvimento e testes). */
  public record Queue(
      @DefaultValue("sqs") String type,
      String fastQueueUrl,
      String standardQueueUrl,
      @DefaultValue("20") @Min(10) int prefetch,
      @DefaultValue("5") @Min(0) int waitSeconds,
      @DefaultValue("30") @Min(1) int visibilityTimeoutSeconds,
      @DefaultValue("5s") Duration retryBaseDelay,
      @DefaultValue("60s") Duration retryMaxDelay) {}

  /** Leitura do DurableInbox: {@code dynamodb} (padrao) ou {@code memory} (testes). */
  public record Inbox(
      @DefaultValue("dynamodb") String type, @DefaultValue("DurableInbox") String tableName) {}

  /** Chave do payload: {@code local} (AES-GCM por variavel de ambiente) ou {@code kms}. */
  public record Payload(
      @DefaultValue("local") String mode,
      String localKey,
      String kmsKeyId,
      @DefaultValue("10000") @Min(1) int kmsDataKeyMaxUses,
      @DefaultValue("PT5M") Duration kmsDataKeyMaxAge) {}
}
