package br.com.consignado.platform.ingestion.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.*;

import br.com.consignado.platform.ingestion.application.port.out.*;
import br.com.consignado.platform.ingestion.domain.IdempotencyMismatchException;
import br.com.consignado.platform.ingestion.domain.InboxUnavailableException;
import br.com.consignado.platform.ingestion.infrastructure.adapter.out.persistence.BusinessFingerprintInboxAdapter;
import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.vo.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

class BusinessFingerprintInboxAdapterTest {
  private static AttributeValue s(String value) {
    return AttributeValue.builder().s(value).build();
  }

  private static final class Dynamo implements DynamoDbClient {
    final Map<String, Map<String, AttributeValue>> records = new HashMap<>();
    TransactWriteItemsRequest last;
    RuntimeException failure;
    boolean race;
    boolean corrupt;
    boolean aliasRace;

    @Override
    public String serviceName() {
      return "dynamodb";
    }

    @Override
    public void close() {}

    @Override
    public GetItemResponse getItem(GetItemRequest r) {
      assertThat(r.consistentRead()).isTrue();
      String key =
          r.tableName()
              + ":"
              + (r.key().containsKey("PK")
                  ? r.key().get("PK").s()
                  : r.key().get("idempotencyKey").s());
      return GetItemResponse.builder().item(records.getOrDefault(key, Map.of())).build();
    }

    @Override
    public TransactWriteItemsResponse transactWriteItems(TransactWriteItemsRequest r) {
      last = r;
      if (failure != null) {
        throw failure;
      }
      for (TransactWriteItem action : r.transactItems()) {
        if (action.put() != null) {
          var put = action.put();
          String key =
              put.tableName()
                  + ":"
                  + (put.item().containsKey("PK")
                      ? put.item().get("PK").s()
                      : put.item().get("idempotencyKey").s());
          records.put(key, new HashMap<>(put.item()));
        }
      }
      if (corrupt) {
        records.values().forEach(record -> record.remove("proposalId"));
        throw TransactionCanceledException.builder().build();
      }
      if (race || (aliasRace && r.transactItems().size() == 2)) {
        race = false;
        aliasRace = false;
        throw TransactionCanceledException.builder().build();
      }
      return TransactWriteItemsResponse.builder().build();
    }
  }

  private final Dynamo client = new Dynamo();
  private final BusinessFingerprintInboxAdapter adapter =
      new BusinessFingerprintInboxAdapter(client, "Inbox", "Dedup");

  private static InboxItem item(String key, String business, String request) {
    return new InboxItem(
        IdempotencyKey.parse(key),
        new TenantId("a"),
        ConvenioType.INSS,
        false,
        Money.of("10"),
        "encrypted",
        1_000L,
        10_000L,
        "trace-original",
        new BusinessFingerprintPort.Fingerprints(business, request));
  }

  private static String uuid() {
    return UUID.randomUUID().toString();
  }

  @Test
  void commits_two_metadata_records_and_one_routable_inbox_with_an_independent_uuid() {
    String key = uuid();
    var result = adapter.putIfAbsent(item(key, "business", "request"));
    assertThat(result.created()).isTrue();
    assertThat(result.proposalId()).isNotEqualTo(key);
    assertThat(client.last.clientRequestToken()).isNotBlank();
    assertThat(client.last.transactItems()).hasSize(3);
    for (var action : client.last.transactItems()) {
      assertThat(action.put().conditionExpression()).isEqualTo("attribute_not_exists(#pk)");
    }
    var payload = client.last.transactItems().get(2).put().item();
    assertThat(payload.get("idempotencyKey").s()).isEqualTo(result.proposalId());
    assertThat(payload.get("status").s()).isEqualTo("RECEIVED");
    assertThat(payload).doesNotContainKeys("PK", "SK", "requestFingerprint");
    assertThat(adapter.putIfAbsent(item(result.proposalId(), "business", "request")).proposalId())
        .isEqualTo(result.proposalId());
    assertThat(adapter.putIfAbsent(item(key, "different-day", "request")).proposalId())
        .isEqualTo(result.proposalId());
    assertThatThrownBy(() -> adapter.putIfAbsent(item(key, "business", "changed")))
        .isInstanceOf(IdempotencyMismatchException.class);
  }

  @Test
  void binds_a_new_header_to_the_original_semantic_proposal_and_checks_alias_payload_on_retries() {
    var first = adapter.putIfAbsent(item(uuid(), "business", "request"));
    String newKey = uuid();
    var alias = adapter.putIfAbsent(item(newKey, "business", "explicit-date-request"));
    assertThat(alias.semanticDuplicate()).isTrue();
    assertThat(alias.proposalId()).isEqualTo(first.proposalId());
    assertThat(client.last.transactItems()).hasSize(2);
    assertThat(client.last.transactItems().getFirst().conditionCheck()).isNotNull();
    var retry = adapter.putIfAbsent(item(newKey, "business", "explicit-date-request"));
    assertThat(retry.semanticDuplicate()).isFalse();
    assertThat(retry.existingCreatedAt()).isEqualTo(1_000L);
    assertThatThrownBy(() -> adapter.putIfAbsent(item(newKey, "business", "changed")))
        .isInstanceOf(IdempotencyMismatchException.class);
  }

  @Test
  void resolves_a_committed_concurrent_winner_but_does_not_classify_throttles_as_duplicates() {
    client.race = true;
    assertThat(adapter.putIfAbsent(item(uuid(), "business", "request")).created()).isFalse();
    client.failure = TransactionCanceledException.builder().build();
    assertThatThrownBy(() -> adapter.putIfAbsent(item(uuid(), "new-business", "request")))
        .isInstanceOf(InboxUnavailableException.class);
    client.failure = SdkClientException.create("sensitive-sdk-details");
    assertThatThrownBy(() -> adapter.putIfAbsent(item(uuid(), "new-business", "request")))
        .isInstanceOf(InboxUnavailableException.class)
        .hasMessageNotContaining("sensitive-sdk-details");
  }

  @Test
  void resolves_concurrent_alias_binding_and_rejects_unverifiable_legacy_keys() {
    adapter.putIfAbsent(item(uuid(), "business", "request"));
    client.aliasRace = true;
    assertThat(adapter.putIfAbsent(item(uuid(), "business", "request")).created()).isFalse();
    String legacy = uuid();
    client.records.put("Inbox:" + legacy, Map.of("idempotencyKey", s(legacy)));
    assertThatThrownBy(() -> adapter.putIfAbsent(item(legacy, "other", "request")))
        .isInstanceOf(IdempotencyMismatchException.class);
  }

  @Test
  void fails_closed_for_corrupt_metadata_and_invalid_table_configuration() {
    client.corrupt = true;
    assertThatThrownBy(() -> adapter.putIfAbsent(item(uuid(), "business", "request")))
        .isInstanceOf(InboxUnavailableException.class);
    assertThatThrownBy(() -> new BusinessFingerprintInboxAdapter(client, "Same", "Same"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
