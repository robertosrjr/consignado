package br.com.consignado.platform.worker.infrastructure.config;

import br.com.consignado.platform.aws.KmsDataKeyProvider;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.crypto.DataKeyProvider;
import br.com.consignado.platform.shared.crypto.LocalDataKeyProvider;
import br.com.consignado.platform.shared.crypto.PayloadEnvelope;
import br.com.consignado.platform.starter.TenantTagPolicy;
import br.com.consignado.platform.worker.application.port.in.ProcessProposalUseCase;
import br.com.consignado.platform.worker.application.port.out.ClockPort;
import br.com.consignado.platform.worker.application.port.out.DecisionRecorderPort;
import br.com.consignado.platform.worker.application.port.out.InboxReadPort;
import br.com.consignado.platform.worker.application.port.out.LaneQueuePort;
import br.com.consignado.platform.worker.application.port.out.LegacyCorePort;
import br.com.consignado.platform.worker.application.port.out.ProposalRepositoryPort;
import br.com.consignado.platform.worker.application.port.out.QueueDepthPort;
import br.com.consignado.platform.worker.application.port.out.ThroughputBudgetPort;
import br.com.consignado.platform.worker.application.usecase.ProcessProposalService;
import br.com.consignado.platform.worker.application.usecase.ProposalDispatchLoop;
import br.com.consignado.platform.worker.application.usecase.RetryBackoff;
import br.com.consignado.platform.worker.domain.LaneBudgetAllocator;
import br.com.consignado.platform.worker.infrastructure.adapter.in.messaging.DispatchLoopRunner;
import br.com.consignado.platform.worker.infrastructure.adapter.out.budget.FixedPerTaskBudgetAdapter;
import br.com.consignado.platform.worker.infrastructure.adapter.out.inbox.DynamoDbDeadlineAdapter;
import br.com.consignado.platform.worker.infrastructure.adapter.out.inbox.DynamoDbInboxReadAdapter;
import br.com.consignado.platform.worker.infrastructure.adapter.out.inbox.InMemoryInboxReadAdapter;
import br.com.consignado.platform.worker.infrastructure.adapter.out.legacy.CircuitBreakerAvailabilityAdapter;
import br.com.consignado.platform.worker.infrastructure.adapter.out.legacy.LegacyHttpAdapter;
import br.com.consignado.platform.worker.infrastructure.adapter.out.legacy.MeteredLegacyCorePort;
import br.com.consignado.platform.worker.infrastructure.adapter.out.messaging.InMemoryLaneQueue;
import br.com.consignado.platform.worker.infrastructure.adapter.out.messaging.SqsLaneQueueAdapter;
import br.com.consignado.platform.worker.infrastructure.adapter.out.persistence.DynamoDbProposalRepository;
import br.com.consignado.platform.worker.infrastructure.adapter.out.persistence.InMemoryProposalRepository;
import br.com.consignado.platform.worker.infrastructure.adapter.out.persistence.ProposalStatusCounts;
import br.com.consignado.platform.worker.infrastructure.metrics.BacklogEstimator;
import br.com.consignado.platform.worker.infrastructure.metrics.BusinessMetrics;
import br.com.consignado.platform.worker.infrastructure.metrics.DeadlineEvaluator;
import br.com.consignado.platform.worker.infrastructure.metrics.QueueDepthSampler;
import br.com.consignado.platform.worker.infrastructure.metrics.WorkerMetrics;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/** Ligacao dos adaptadores as portas. Endpoints AWS vem de variaveis (AWS_ENDPOINT_URL_*). */
@Configuration(proxyBeanMethods = false)
public class WorkerConfig {

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "worker.sre.capture-file")
  br.com.consignado.platform.worker.infrastructure.metrics.SreSnapshotExporter sreSnapshotExporter(
      org.springframework.beans.factory.ObjectProvider<InMemoryProposalRepository> repository,
      org.springframework.core.env.Environment environment)
      throws Exception {
    InMemoryProposalRepository memory = repository.getIfAvailable();
    if (memory == null) {
      throw new IllegalStateException(
          "worker.sre.capture-file requer worker.proposals.type=memory (captura de experimento)");
    }
    return new br.com.consignado.platform.worker.infrastructure.metrics.SreSnapshotExporter(
        memory, java.nio.file.Path.of(environment.getRequiredProperty("worker.sre.capture-file")));
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  ClockPort clockPort(Clock clock) {
    return clock::instant;
  }

  @Bean
  @ConditionalOnProperty(
      name = "worker.proposals.type",
      havingValue = "memory",
      matchIfMissing = true)
  InMemoryProposalRepository proposalRepository(ClockPort clock) {
    return new InMemoryProposalRepository(clock);
  }

  @Bean
  CircuitBreaker legacyCircuitBreaker(WorkerProperties properties) {
    WorkerProperties.Legacy legacy = properties.legacy();
    return CircuitBreaker.of(
        "legacy",
        LegacyHttpAdapter.breakerConfig(
            legacy.failureRatePercent(),
            legacy.slowCallThreshold(),
            legacy.minimumCalls(),
            legacy.windowSize(),
            legacy.openStateWait()));
  }

  @Bean
  LegacyCorePort legacyCorePort(
      WorkerProperties properties, CircuitBreaker legacyCircuitBreaker, MeterRegistry registry) {
    WorkerProperties.Legacy legacy = properties.legacy();
    HttpClient http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(1))
            .build();
    LegacyHttpAdapter adapter =
        new LegacyHttpAdapter(
            http,
            URI.create(legacy.baseUrl()),
            legacy.apiKey(),
            legacy.requestTimeout(),
            legacyCircuitBreaker);
    return new MeteredLegacyCorePort(adapter, registry);
  }

  @Bean
  ThroughputBudgetPort throughputBudgetPort(WorkerProperties properties) {
    WorkerProperties.Budget budget = properties.budget();
    return new FixedPerTaskBudgetAdapter(
        budget.totalTps(),
        budget.instances(),
        budget.fastShare(),
        budget.safetyMargin(),
        budget.bucketCapacity(),
        System::nanoTime);
  }

  @Bean
  BacklogEstimator backlogEstimator() {
    return new BacklogEstimator();
  }

  @Bean
  WorkerMetrics workerMetrics(
      MeterRegistry registry,
      ProposalStatusCounts repository,
      BacklogEstimator estimator,
      CircuitBreaker legacyCircuitBreaker) {
    return new WorkerMetrics(registry, repository, estimator, legacyCircuitBreaker);
  }

  @Bean
  QueueDepthSampler queueDepthSampler(
      MeterRegistry registry,
      QueueDepthPort depths,
      WorkerMetrics metrics,
      BacklogEstimator estimator,
      WorkerProperties properties) {
    return new QueueDepthSampler(
        registry, depths, metrics, estimator, properties.metricsSamplePeriodSeconds());
  }

  @Bean
  BusinessMetrics businessMetrics(MeterRegistry registry, TenantTagPolicy tenantTags) {
    return new BusinessMetrics(registry, tenantTags);
  }

  @Bean
  ProcessProposalUseCase processProposalUseCase(
      ProposalRepositoryPort repository,
      InboxReadPort inbox,
      LegacyCorePort legacy,
      ClockPort clock,
      BusinessMetrics business,
      DecisionRecorderPort decisions) {
    return new ProcessProposalService(repository, inbox, legacy, clock, business, decisions);
  }

  @Bean
  ExecutorService dispatchExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  @Bean
  ProposalDispatchLoop proposalDispatchLoop(
      LaneQueuePort queue,
      ThroughputBudgetPort budget,
      ProcessProposalUseCase useCase,
      ExecutorService dispatchExecutor,
      WorkerProperties properties,
      WorkerMetrics metrics,
      CircuitBreaker legacyCircuitBreaker) {
    return new ProposalDispatchLoop(
        queue,
        budget,
        useCase,
        new LaneBudgetAllocator(),
        dispatchExecutor,
        properties.maxInFlight(),
        new RetryBackoff(properties.queue().retryBaseDelay(), properties.queue().retryMaxDelay()),
        metrics,
        new CircuitBreakerAvailabilityAdapter(legacyCircuitBreaker));
  }

  /**
   * Saturacao (quarto sinal dourado): ocupacao da concorrencia do laco de despacho. Gauges locais
   * ao processo. Usa MeterBinder porque a metrica depende do laco, criado depois das metricas.
   *
   * <p>Lei de Little: para sustentar {@code lambda} mensagens/s com latencia {@code W} do legado e
   * preciso {@code lambda * W} em voo. Se {@code worker_in_flight} encosta em {@code
   * worker_in_flight_max}, o limite de concorrencia (e nao o orcamento de 180 TPS) e o gargalo: a
   * vazao cai para {@code maxInFlight / W}.
   */
  @Bean
  MeterBinder dispatchSaturationMetrics(ProposalDispatchLoop loop) {
    return registry -> {
      Gauge.builder("worker.in.flight", loop, ProposalDispatchLoop::inFlight)
          .description("Mensagens em processamento neste instante (processo local)")
          .register(registry);
      Gauge.builder("worker.in.flight.max", loop, ProposalDispatchLoop::maxInFlight)
          .description("Concorrencia maxima configurada (worker.max-in-flight)")
          .register(registry);
    };
  }

  @Bean
  DispatchLoopRunner dispatchLoopRunner(
      ProposalDispatchLoop loop, ExecutorService dispatchExecutor) {
    return new DispatchLoopRunner(loop, dispatchExecutor);
  }

  // --- DurableInbox (leitura) -----------------------------------------------------------------

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(
      name = "worker.inbox.type",
      havingValue = "dynamodb",
      matchIfMissing = true)
  static class DynamoInboxConfig {

    @Bean
    @ConditionalOnProperty(
        name = "worker.payload.mode",
        havingValue = "local",
        matchIfMissing = true)
    DataKeyProvider localDataKeyProvider(WorkerProperties properties) {
      return LocalDataKeyProvider.fromBase64(properties.payload().localKey());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "worker.payload.mode", havingValue = "kms")
    KmsClient kmsClient() {
      return KmsClient.create();
    }

    @Bean
    @ConditionalOnProperty(name = "worker.payload.mode", havingValue = "kms")
    DataKeyProvider kmsDataKeyProvider(
        KmsClient kmsClient, WorkerProperties properties, Clock clock) {
      WorkerProperties.Payload payload = properties.payload();
      return new KmsDataKeyProvider(
          kmsClient,
          payload.kmsKeyId(),
          payload.kmsDataKeyMaxUses(),
          payload.kmsDataKeyMaxAge(),
          clock);
    }

    @Bean(destroyMethod = "close")
    DynamoDbClient dynamoDbClient(WorkerProperties properties) {
      // Uma conexao por mensagem em voo: o pool padrao (50) seria o gargalo abaixo de
      // max-in-flight.
      return DynamoDbClient.builder()
          .httpClientBuilder(Apache5HttpClient.builder().maxConnections(properties.maxInFlight()))
          .build();
    }

    @Bean
    InboxReadPort dynamoInbox(
        DynamoDbClient dynamoDbClient,
        WorkerProperties properties,
        DataKeyProvider dataKeyProvider) {
      return new DynamoDbInboxReadAdapter(
          dynamoDbClient, properties.inbox().tableName(), new PayloadEnvelope(dataKeyProvider));
    }

    @Bean
    @ConditionalOnProperty(name = "worker.proposals.type", havingValue = "dynamodb")
    DynamoDbProposalRepository durableProposalRepository(
        DynamoDbClient dynamoDbClient, WorkerProperties properties, ClockPort clock) {
      return new DynamoDbProposalRepository(
          dynamoDbClient, properties.proposals().tableName(), clock);
    }

    @Bean
    DynamoDbDeadlineAdapter deadlineAdapter(
        DynamoDbClient dynamoDbClient, WorkerProperties properties) {
      return new DynamoDbDeadlineAdapter(dynamoDbClient, properties.inbox().tableName());
    }

    @Bean
    @ConditionalOnProperty(name = "worker.slo.enabled", havingValue = "true", matchIfMissing = true)
    DeadlineEvaluator deadlineEvaluator(
        MeterRegistry registry,
        DynamoDbDeadlineAdapter deadlineAdapter,
        Clock clock,
        WorkerProperties properties) {
      WorkerProperties.Slo slo = properties.slo();
      return new DeadlineEvaluator(
          registry,
          deadlineAdapter,
          clock,
          Map.of(Lane.FAST, slo.fastDeadline(), Lane.STANDARD, slo.standardDeadline()),
          slo.evaluationPeriod(),
          slo.batchSize());
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(name = "worker.inbox.type", havingValue = "memory")
  static class MemoryInboxConfig {

    @Bean
    InMemoryInboxReadAdapter memoryInbox() {
      return new InMemoryInboxReadAdapter();
    }

    @Bean
    DecisionRecorderPort noDecisionRecorder() {
      return DecisionRecorderPort.NONE;
    }
  }

  // --- Filas das lanes ------------------------------------------------------------------------

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(name = "worker.queue.type", havingValue = "sqs", matchIfMissing = true)
  static class SqsQueueConfig {

    @Bean(destroyMethod = "close")
    SqsClient sqsClient() {
      return SqsClient.create();
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    SqsLaneQueueAdapter sqsLaneQueue(SqsClient sqsClient, WorkerProperties properties) {
      WorkerProperties.Queue queue = properties.queue();
      if (isBlank(queue.fastQueueUrl()) || isBlank(queue.standardQueueUrl())) {
        throw new IllegalStateException(
            "worker.queue.fast-queue-url e worker.queue.standard-queue-url sao obrigatorias");
      }
      return new SqsLaneQueueAdapter(
          sqsClient,
          queue.fastQueueUrl(),
          queue.standardQueueUrl(),
          queue.dlqQueueUrl(),
          queue.prefetch(),
          queue.waitSeconds(),
          queue.visibilityTimeoutSeconds());
    }

    private static boolean isBlank(String value) {
      return value == null || value.isBlank();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(name = "worker.queue.type", havingValue = "memory")
  static class MemoryQueueConfig {

    @Bean
    InMemoryLaneQueue memoryLaneQueue() {
      return new InMemoryLaneQueue(3);
    }
  }
}
