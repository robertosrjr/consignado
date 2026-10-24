package br.com.consignado.platform.worker.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.localenv.LocalDynamoDb;
import br.com.consignado.platform.shared.contract.InboxAttributes;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.worker.application.port.out.DeadlineIndexPort.OpenProposal;
import br.com.consignado.platform.worker.infrastructure.adapter.out.inbox.DynamoDbDeadlineAdapter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/**
 * DynamoDB Local real (sem Docker): o indice esparso de prazos so devolve itens abertos e vencidos,
 * a decisao e gravada uma vez e o fechamento condicional conta cada proposta uma vez.
 */
class DynamoDbDeadlineAdapterIT {

  private static final String TABLE = "DurableInboxSlo";

  private static LocalDynamoDb dynamo;
  private static DynamoDbClient client;
  private static DynamoDbDeadlineAdapter adapter;

  @BeforeAll
  static void start() throws Exception {
    dynamo = LocalDynamoDb.start(0);
    client = dynamo.client();
    dynamo.createInboxTable(client, TABLE);
    adapter = new DynamoDbDeadlineAdapter(client, TABLE);
  }

  @AfterAll
  static void stop() throws Exception {
    client.close();
    dynamo.close();
  }

  private static IdempotencyKey insertRouted(String lane, long createdAt) {
    IdempotencyKey key = IdempotencyKey.parse(UUID.randomUUID().toString());
    client.putItem(
        PutItemRequest.builder()
            .tableName(TABLE)
            .item(
                Map.of(
                    InboxAttributes.IDEMPOTENCY_KEY,
                    AttributeValue.builder().s(key.toString()).build(),
                    InboxAttributes.CREATED_AT,
                    AttributeValue.builder().n(Long.toString(createdAt)).build(),
                    InboxAttributes.SLO_OPEN,
                    AttributeValue.builder().s(lane).build()))
            .build());
    return key;
  }

  private static Map<String, AttributeValue> item(IdempotencyKey key) {
    return client
        .getItem(
            GetItemRequest.builder()
                .tableName(TABLE)
                .consistentRead(true)
                .key(
                    Map.of(
                        InboxAttributes.IDEMPOTENCY_KEY,
                        AttributeValue.builder().s(key.toString()).build()))
                .build())
        .item();
  }

  @Test
  void indiceDevolveSomenteAbertasDaLaneAteOCorteComADecisaoProjetada() {
    IdempotencyKey due = insertRouted("fast", 1_000);
    insertRouted("fast", 5_000); // depois do corte
    insertRouted("standard", 1_000); // outra lane
    adapter.recordDecision(due, true, Instant.ofEpochMilli(1_500));

    List<OpenProposal> open = adapter.acceptedUpTo(Lane.FAST, 2_000, 100);

    assertThat(open).containsExactly(new OpenProposal(due, 1_000, 1_500L));
  }

  @Test
  void decisaoEGravadaUmaUnicaVez() {
    IdempotencyKey key = insertRouted("standard", 10_000);

    adapter.recordDecision(key, false, Instant.ofEpochMilli(11_000));
    adapter.recordDecision(key, true, Instant.ofEpochMilli(99_000));

    Map<String, AttributeValue> item = item(key);
    assertThat(item.get(InboxAttributes.DECISION).s())
        .isEqualTo(InboxAttributes.DECISION_BUSINESS_REJECTED);
    assertThat(item.get(InboxAttributes.DECIDED_AT).n()).isEqualTo("11000");
  }

  @Test
  void decisaoNaoCriaItemInexistente() {
    IdempotencyKey missing = IdempotencyKey.parse(UUID.randomUUID().toString());

    adapter.recordDecision(missing, true, Instant.ofEpochMilli(1));

    assertThat(item(missing)).isEmpty();
  }

  @Test
  void fechamentoRemoveDoIndiceEConcorrenteNaoFechaDeNovo() {
    IdempotencyKey key = insertRouted("standard", 20_000);

    assertThat(adapter.close(key, "bad")).isTrue();
    assertThat(adapter.close(key, "good")).isFalse();

    assertThat(item(key).get(InboxAttributes.SLO_OUTCOME).s()).isEqualTo("bad");
    assertThat(adapter.acceptedUpTo(Lane.STANDARD, 30_000, 100))
        .extracting(OpenProposal::key)
        .doesNotContain(key);
  }
}
