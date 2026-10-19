package br.com.consignado.platform.ingestion.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.*;

import br.com.consignado.platform.ingestion.application.port.in.*;
import br.com.consignado.platform.ingestion.application.port.out.*;
import br.com.consignado.platform.ingestion.application.service.BusinessFingerprintService;
import br.com.consignado.platform.ingestion.application.usecase.AcceptProposalService;
import br.com.consignado.platform.ingestion.domain.*;
import br.com.consignado.platform.ingestion.infrastructure.adapter.out.persistence.BusinessFingerprintInboxAdapter;
import br.com.consignado.platform.localenv.LocalDynamoDb;
import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.vo.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

/** Real DynamoDB Local: transaction atomicity and races; fake clients alone cannot prove these. */
class BusinessFingerprintInboxAdapterIT {
  static LocalDynamoDb server;
  static DynamoDbClient client;

  @BeforeAll
  static void start() throws Exception {
    server = LocalDynamoDb.start(0);
    client = server.client();
    server.createInboxTable(client, "FingerprintInbox");
    client.createTable(
        CreateTableRequest.builder()
            .tableName("Dedup")
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .attributeDefinitions(
                AttributeDefinition.builder()
                    .attributeName("PK")
                    .attributeType(ScalarAttributeType.S)
                    .build(),
                AttributeDefinition.builder()
                    .attributeName("SK")
                    .attributeType(ScalarAttributeType.S)
                    .build())
            .keySchema(
                KeySchemaElement.builder().attributeName("PK").keyType(KeyType.HASH).build(),
                KeySchemaElement.builder().attributeName("SK").keyType(KeyType.RANGE).build())
            .build());
  }

  @AfterAll
  static void stop() throws Exception {
    client.close();
    server.close();
  }

  private static AcceptProposalService service(String inbox) {
    return service(client, inbox);
  }

  private static AcceptProposalService service(DynamoDbClient store, String inbox) {
    return new AcceptProposalService(
        t -> true,
        p -> "encrypted",
        new BusinessFingerprintInboxAdapter(store, inbox, "Dedup"),
        () -> Instant.parse("2026-10-16T12:00:00Z"),
        () -> UUID.randomUUID().toString(),
        Duration.ofDays(7),
        BusinessFingerprintService.local(new byte[32], "v2"));
  }

  private static AcceptProposalCommand command(String tenant, String key, String amount) {
    return new AcceptProposalCommand(
        IdempotencyKey.parse(key),
        new TenantId(tenant),
        Cpf.of("52998224725"),
        ConvenioType.INSS,
        false,
        Money.of(amount),
        84,
        LocalDate.of(2026, 10, 16));
  }

  @Test
  void parallel_new_keys_produce_one_inbox_and_all_retries_track_the_original() throws Exception {
    String tenant = "race-" + UUID.randomUUID().toString().substring(0, 8);
    var tasks = new ArrayList<Callable<AcceptProposalResult>>();
    for (int i = 0; i < 40; i++) {
      String key = UUID.randomUUID().toString();
      tasks.add(
          () -> {
            // DynamoDB may return a transient transaction conflict; retry the SAME technical key.
            for (int attempt = 0; attempt < 20; attempt++) {
              try {
                return service("FingerprintInbox").accept(command(tenant, key, "15000"));
              } catch (InboxUnavailableException e) {
                if (attempt == 19) throw e;
              }
            }
            throw new AssertionError("unreachable");
          });
    }
    var results = new ArrayList<AcceptProposalResult>();
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      for (var result : pool.invokeAll(tasks)) {
        results.add(result.get());
      }
    }
    assertThat(results.stream().filter(r -> r.outcome() == IngestionOutcome.RECEIVED).count())
        .isEqualTo(1);
    assertThat(results.stream().map(AcceptProposalResult::proposalId).distinct().count())
        .isEqualTo(1);
    var stored =
        client
            .getItem(
                GetItemRequest.builder()
                    .tableName("FingerprintInbox")
                    .consistentRead(true)
                    .key(
                        Map.of(
                            "idempotencyKey",
                            AttributeValue.builder().s(results.getFirst().proposalId()).build()))
                    .build())
            .item();
    assertThat(stored.get("status").s()).isEqualTo("RECEIVED");
    assertThat(stored.toString()).doesNotContain("52998224725");
    var first = results.getFirst();
    assertThatThrownBy(
            () ->
                service("FingerprintInbox")
                    .accept(command(tenant, first.idempotencyKey().toString(), "16000")))
        .isInstanceOf(IdempotencyMismatchException.class);
    // A new adapter/service instance still retrieves the original durable identity.
    assertThat(
            service("FingerprintInbox")
                .accept(command(tenant, first.idempotencyKey().toString(), "15000"))
                .proposalId())
        .isEqualTo(first.proposalId());
  }

  @Test
  void failed_transaction_does_not_leave_a_reserved_fingerprint() {
    String tenant = "rollback-" + UUID.randomUUID().toString().substring(0, 8);
    String key = UUID.randomUUID().toString();
    // Reads succeed; only the transaction is altered to include an invalid destination table.
    // All writes still go to the real DynamoDB Local engine, proving atomic rollback.
    DynamoDbClient failingTransaction =
        new DynamoDbClient() {
          @Override
          public String serviceName() {
            return "dynamodb";
          }

          @Override
          public void close() {}

          @Override
          public GetItemResponse getItem(GetItemRequest request) {
            return client.getItem(request);
          }

          @Override
          public TransactWriteItemsResponse transactWriteItems(TransactWriteItemsRequest request) {
            var actions = new ArrayList<>(request.transactItems());
            var inboxPut = actions.getLast().put();
            actions.set(
                actions.size() - 1,
                TransactWriteItem.builder()
                    .put(inboxPut.toBuilder().tableName("MissingInbox").build())
                    .build());
            return client.transactWriteItems(request.toBuilder().transactItems(actions).build());
          }
        };
    assertThatThrownBy(
            () -> service(failingTransaction, "FingerprintInbox").accept(command(tenant, key, "1")))
        .isInstanceOf(InboxUnavailableException.class);
    assertThat(service("FingerprintInbox").accept(command(tenant, key, "1")).outcome())
        .isEqualTo(IngestionOutcome.RECEIVED);
  }
}
