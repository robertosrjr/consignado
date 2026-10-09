package br.com.consignado.platform.ingestion.infrastructure.config;

import br.com.consignado.platform.aws.KmsDataKeyProvider;
import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalUseCase;
import br.com.consignado.platform.ingestion.application.port.out.ClockPort;
import br.com.consignado.platform.ingestion.application.port.out.DurableInboxPort;
import br.com.consignado.platform.ingestion.application.port.out.PayloadProtectionPort;
import br.com.consignado.platform.ingestion.application.port.out.TenantQuotaPort;
import br.com.consignado.platform.ingestion.application.port.out.TracingIdPort;
import br.com.consignado.platform.ingestion.application.usecase.AcceptProposalService;
import br.com.consignado.platform.ingestion.infrastructure.adapter.in.web.MeteredAcceptProposalUseCase;
import br.com.consignado.platform.ingestion.infrastructure.adapter.out.crypto.EnvelopePayloadProtectionAdapter;
import br.com.consignado.platform.ingestion.infrastructure.adapter.out.persistence.CoalescingInboxAdapter;
import br.com.consignado.platform.ingestion.infrastructure.adapter.out.persistence.DynamoDbInboxAdapter;
import br.com.consignado.platform.ingestion.infrastructure.adapter.out.persistence.InMemoryInboxAdapter;
import br.com.consignado.platform.ingestion.infrastructure.adapter.out.quota.Resilience4jTenantQuotaAdapter;
import br.com.consignado.platform.ingestion.infrastructure.adapter.out.support.SystemTracingIdAdapter;
import br.com.consignado.platform.shared.crypto.DataKeyProvider;
import br.com.consignado.platform.shared.crypto.LocalDataKeyProvider;
import br.com.consignado.platform.shared.crypto.PayloadEnvelope;
import br.com.consignado.platform.starter.TenantTagPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.kms.KmsClient;

/** Ligacao dos adaptadores as portas. Endpoints AWS vem de variaveis (AWS_ENDPOINT_URL_*). */
@Configuration(proxyBeanMethods = false)
public class IngestionConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  ClockPort clockPort(Clock clock) {
    return clock::instant;
  }

  @Bean
  TracingIdPort tracingIdPort() {
    return new SystemTracingIdAdapter();
  }

  @Bean
  TenantQuotaPort tenantQuotaPort(IngestionProperties properties) {
    IngestionProperties.Quota quota = properties.quota();
    return new Resilience4jTenantQuotaAdapter(
        quota.defaultPerSecond(), quota.tenants(), quota.maxTenants());
  }

  // --- DurableInbox ---------------------------------------------------------------------------

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(
      name = "ingestion.inbox.type",
      havingValue = "dynamodb",
      matchIfMissing = true)
  DynamoDbClient dynamoDbClient(IngestionProperties properties) {
    IngestionProperties.Inbox inbox = properties.inbox();
    return DynamoDbClient.builder()
        .httpClientBuilder(Apache5HttpClient.builder().maxConnections(inbox.maxConnections()))
        .overrideConfiguration(
            ClientOverrideConfiguration.builder()
                .apiCallTimeout(inbox.apiCallTimeout())
                .apiCallAttemptTimeout(inbox.apiCallAttemptTimeout())
                .retryStrategy(
                    AwsRetryStrategy.standardRetryStrategy().toBuilder().maxAttempts(2).build())
                .build())
        .build();
  }

  @Bean
  @ConditionalOnProperty(
      name = "ingestion.inbox.type",
      havingValue = "dynamodb",
      matchIfMissing = true)
  DurableInboxPort dynamoDbInbox(DynamoDbClient dynamoDbClient, IngestionProperties properties) {
    IngestionProperties.Inbox inbox = properties.inbox();
    DurableInboxPort dynamo = new DynamoDbInboxAdapter(dynamoDbClient, inbox.tableName());
    if (inbox.recentKeysMaxEntries() == 0) {
      return dynamo;
    }
    // Tempestade de duplicatas: uma escrita por chave por instancia; quem espera, espera no maximo
    // o mesmo prazo da chamada ao DynamoDB
    return new CoalescingInboxAdapter(
        dynamo,
        inbox.recentKeysTtl(),
        inbox.apiCallTimeout(),
        inbox.recentKeysMaxEntries(),
        System::nanoTime);
  }

  @Bean
  @ConditionalOnProperty(name = "ingestion.inbox.type", havingValue = "memory")
  InMemoryInboxAdapter inMemoryInbox() {
    return new InMemoryInboxAdapter();
  }

  // --- Protecao do payload --------------------------------------------------------------------

  @Bean
  @ConditionalOnProperty(
      name = "ingestion.payload.mode",
      havingValue = "local",
      matchIfMissing = true)
  DataKeyProvider localDataKeyProvider(IngestionProperties properties) {
    return LocalDataKeyProvider.fromBase64(properties.payload().localKey());
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "ingestion.payload.mode", havingValue = "kms")
  KmsClient kmsClient() {
    return KmsClient.create();
  }

  @Bean
  @ConditionalOnProperty(name = "ingestion.payload.mode", havingValue = "kms")
  DataKeyProvider kmsDataKeyProvider(
      KmsClient kmsClient, IngestionProperties properties, Clock clock) {
    IngestionProperties.Payload payload = properties.payload();
    return new KmsDataKeyProvider(
        kmsClient,
        payload.kmsKeyId(),
        payload.kmsDataKeyMaxUses(),
        payload.kmsDataKeyMaxAge(),
        clock);
  }

  @Bean
  PayloadProtectionPort payloadProtectionPort(DataKeyProvider dataKeyProvider) {
    return new EnvelopePayloadProtectionAdapter(new PayloadEnvelope(dataKeyProvider));
  }

  // --- Caso de uso ----------------------------------------------------------------------------

  @Bean
  AcceptProposalUseCase acceptProposalUseCase(
      TenantQuotaPort quota,
      PayloadProtectionPort protection,
      DurableInboxPort inbox,
      ClockPort clock,
      TracingIdPort tracing,
      IngestionProperties properties,
      MeterRegistry registry,
      TenantTagPolicy tenantTags) {
    AcceptProposalService service =
        new AcceptProposalService(
            quota, protection, inbox, clock, tracing, properties.inbox().retention());
    return new MeteredAcceptProposalUseCase(service, registry, tenantTags);
  }
}
