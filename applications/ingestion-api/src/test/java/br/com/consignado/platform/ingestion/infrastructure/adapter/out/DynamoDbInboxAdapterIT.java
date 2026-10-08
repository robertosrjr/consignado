package br.com.consignado.platform.ingestion.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalCommand;
import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalResult;
import br.com.consignado.platform.ingestion.application.usecase.AcceptProposalService;
import br.com.consignado.platform.ingestion.domain.InboxUnavailableException;
import br.com.consignado.platform.ingestion.domain.IngestionOutcome;
import br.com.consignado.platform.ingestion.infrastructure.adapter.out.crypto.EnvelopePayloadProtectionAdapter;
import br.com.consignado.platform.ingestion.infrastructure.adapter.out.persistence.DynamoDbInboxAdapter;
import br.com.consignado.platform.ingestion.infrastructure.adapter.out.support.SystemTracingIdAdapter;
import br.com.consignado.platform.localenv.LocalDynamoDb;
import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.contract.InboxAttributes;
import br.com.consignado.platform.shared.crypto.LocalDataKeyProvider;
import br.com.consignado.platform.shared.crypto.PayloadEnvelope;
import br.com.consignado.platform.shared.vo.Cpf;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.shared.vo.TenantId;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

/**
 * Integracao com o DynamoDB Local real (sem Docker): escrita condicional atomica, idempotencia sob
 * concorrencia (o criterio central do DoD) e protecao do CPF nos atributos gravados.
 */
class DynamoDbInboxAdapterIT {

  private static final String CPF = "52998224725";

  private static LocalDynamoDb dynamo;
  private static DynamoDbClient client;

  @BeforeAll
  static void start() throws Exception {
    dynamo = LocalDynamoDb.start(0);
    client = dynamo.client();
    dynamo.createInboxTable(client, InboxAttributes.TABLE_NAME);
  }

  @AfterAll
  static void stop() throws Exception {
    client.close();
    dynamo.close();
  }

  private static AcceptProposalService service(DynamoDbInboxAdapter inbox) {
    PayloadEnvelope envelope = new PayloadEnvelope(new LocalDataKeyProvider(new byte[32]));
    return new AcceptProposalService(
        tenant -> true,
        new EnvelopePayloadProtectionAdapter(envelope),
        inbox,
        Instant::now,
        new SystemTracingIdAdapter(),
        Duration.ofDays(7));
  }

  private static AcceptProposalCommand command(IdempotencyKey key) {
    return new AcceptProposalCommand(
        key,
        new TenantId("banco-a"),
        new Cpf(CPF),
        ConvenioType.INSS,
        true,
        Money.of("15000.50"),
        84);
  }

  @Test
  void should_accept_once_and_answer_duplicate_with_the_original_tracing_id_and_time() {
    AcceptProposalService service =
        service(new DynamoDbInboxAdapter(client, InboxAttributes.TABLE_NAME));
    IdempotencyKey key = new IdempotencyKey(UUID.randomUUID());

    AcceptProposalResult first = service.accept(command(key));
    AcceptProposalResult second = service.accept(command(key));

    assertThat(first.outcome()).isEqualTo(IngestionOutcome.RECEIVED);
    assertThat(second.outcome()).isEqualTo(IngestionOutcome.DUPLICATE_ACCEPTED);
    assertThat(second.tracingId()).isEqualTo(first.tracingId());
    assertThat(second.receivedAt().toEpochMilli()).isEqualTo(first.receivedAt().toEpochMilli());
  }

  @Test
  void should_store_routing_attributes_in_clear_but_never_the_cpf() {
    AcceptProposalService service =
        service(new DynamoDbInboxAdapter(client, InboxAttributes.TABLE_NAME));
    IdempotencyKey key = new IdempotencyKey(UUID.randomUUID());
    service.accept(command(key));

    Map<String, AttributeValue> item =
        client
            .getItem(
                GetItemRequest.builder()
                    .tableName(InboxAttributes.TABLE_NAME)
                    .consistentRead(true)
                    .key(
                        Map.of(
                            InboxAttributes.IDEMPOTENCY_KEY,
                            AttributeValue.builder().s(key.toString()).build()))
                    .build())
            .item();

    assertThat(item.get(InboxAttributes.STATUS).s()).isEqualTo("RECEIVED");
    assertThat(item.get(InboxAttributes.TENANT_ID).s()).isEqualTo("banco-a");
    assertThat(item.get(InboxAttributes.CONVENIO_TYPE).s()).isEqualTo("INSS");
    assertThat(item.get(InboxAttributes.PORTABILITY).bool()).isTrue();
    assertThat(new java.math.BigDecimal(item.get(InboxAttributes.AMOUNT).n()))
        .isEqualByComparingTo("15000.50");
    assertThat(Long.parseLong(item.get(InboxAttributes.TTL).n()))
        .isGreaterThan(Instant.now().plus(Duration.ofDays(6)).getEpochSecond());
    assertThat(item.get(InboxAttributes.PAYLOAD).s()).startsWith("v1.");
    assertThat(item.toString()).doesNotContain(CPF);
  }

  @Test
  void should_write_exactly_one_item_when_thousands_of_requests_share_the_same_key()
      throws Exception {
    AcceptProposalService service =
        service(new DynamoDbInboxAdapter(client, InboxAttributes.TABLE_NAME));
    IdempotencyKey key = new IdempotencyKey(UUID.randomUUID());
    int requests = 3_000;
    CountDownLatch start = new CountDownLatch(1);
    List<Future<AcceptProposalResult>> futures = new ArrayList<>();
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int i = 0; i < requests; i++) {
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  return service.accept(command(key));
                }));
      }
      start.countDown();
    }

    long received = 0;
    long duplicates = 0;
    java.util.Set<String> tracingIds = new java.util.HashSet<>();
    for (Future<AcceptProposalResult> future : futures) {
      AcceptProposalResult result = future.get();
      if (result.outcome() == IngestionOutcome.RECEIVED) {
        received++;
      } else {
        duplicates++;
      }
      tracingIds.add(result.tracingId());
    }

    assertThat(received).as("exatamente um RECEIVED").isEqualTo(1);
    assertThat(duplicates).isEqualTo(requests - 1);
    assertThat(tracingIds).as("todos recebem o tracingId original").hasSize(1);
  }

  @Test
  void should_not_lose_distinct_proposals_written_in_parallel() throws Exception {
    AcceptProposalService service =
        service(new DynamoDbInboxAdapter(client, InboxAttributes.TABLE_NAME));
    int proposals = 500;
    List<Future<AcceptProposalResult>> futures = new ArrayList<>();
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int i = 0; i < proposals; i++) {
        futures.add(
            executor.submit(() -> service.accept(command(new IdempotencyKey(UUID.randomUUID())))));
      }
    }

    for (Future<AcceptProposalResult> future : futures) {
      assertThat(future.get().outcome()).isEqualTo(IngestionOutcome.RECEIVED);
    }
  }

  @Test
  void should_report_the_inbox_as_unavailable_when_dynamodb_cannot_be_reached() {
    DynamoDbClient unreachable =
        DynamoDbClient.builder()
            .endpointOverride(URI.create("http://127.0.0.1:1"))
            .region(Region.SA_EAST_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
            .httpClient(UrlConnectionHttpClient.create())
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .apiCallTimeout(Duration.ofSeconds(2))
                    .apiCallAttemptTimeout(Duration.ofMillis(500))
                    .build())
            .build();
    AcceptProposalService service =
        service(new DynamoDbInboxAdapter(unreachable, InboxAttributes.TABLE_NAME));

    assertThatThrownBy(() -> service.accept(command(new IdempotencyKey(UUID.randomUUID()))))
        .isInstanceOf(InboxUnavailableException.class);
  }
}
