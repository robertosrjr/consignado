package br.com.consignado.platform.ingestion.infrastructure.adapter.out.persistence;

import br.com.consignado.platform.ingestion.application.port.out.DurableInboxPort;
import br.com.consignado.platform.ingestion.application.port.out.InboxItem;
import br.com.consignado.platform.ingestion.application.port.out.InboxWriteResult;
import br.com.consignado.platform.ingestion.domain.IdempotencyMismatchException;
import br.com.consignado.platform.ingestion.domain.InboxUnavailableException;
import br.com.consignado.platform.shared.contract.InboxAttributes;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionCheck;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/** Technical alias + semantic claim + routable inbox are committed together, in two tables. */
public final class BusinessFingerprintInboxAdapter implements DurableInboxPort {
  private final DynamoDbClient dynamo;
  private final String inboxTable;
  private final String dedupTable;

  public BusinessFingerprintInboxAdapter(
      DynamoDbClient dynamo, String inboxTable, String dedupTable) {
    this.dynamo = dynamo;
    this.inboxTable = inboxTable;
    this.dedupTable = dedupTable;
    if (inboxTable.equals(dedupTable)) {
      throw new IllegalArgumentException("Fingerprint metadata requires a separate PK/SK table");
    }
  }

  @Override
  public InboxWriteResult putIfAbsent(InboxItem item) {
    Objects.requireNonNull(item.fingerprints(), "fingerprints");
    String headerKey = "HEADER#" + item.tenant().value() + "#" + item.idempotencyKey();
    String hashKey = "HASH#" + item.fingerprints().business();
    try {
      Map<String, AttributeValue> header = read(dedupTable, key(headerKey));
      if (!header.isEmpty()) {
        return technical(header, item);
      }
      // Old records have no verifiable fingerprint. Never silently re-accept a known legacy key.
      Map<String, AttributeValue> legacy =
          read(
              inboxTable,
              Map.of(InboxAttributes.IDEMPOTENCY_KEY, s(item.idempotencyKey().toString())));
      if (!legacy.isEmpty() && !"business-fingerprint-v2".equals(value(legacy, "recordOrigin"))) {
        throw new IdempotencyMismatchException();
      }
      Map<String, AttributeValue> business = read(dedupTable, key(hashKey));
      if (!business.isEmpty()) {
        return bindAlias(headerKey, hashKey, business, item);
      }
      String proposalId = UUID.randomUUID().toString();
      Map<String, AttributeValue> metadata = metadata(item, proposalId);
      InboxItem canonical =
          new InboxItem(
              IdempotencyKey.parse(proposalId),
              item.tenant(),
              item.convenio(),
              item.portability(),
              item.amount(),
              item.protectedPayload(),
              item.createdAtEpochMillis(),
              item.ttlEpochSeconds(),
              item.tracingId());
      Map<String, AttributeValue> inboxAttributes = DynamoDbInboxAdapter.toAttributes(canonical);
      inboxAttributes.put("recordOrigin", s("business-fingerprint-v2"));
      if (item.fingerprints().businessDate() != null) {
        inboxAttributes.put("businessDate", s(item.fingerprints().businessDate().toString()));
      }
      dynamo.transactWriteItems(
          TransactWriteItemsRequest.builder()
              .clientRequestToken(UUID.randomUUID().toString())
              .transactItems(
                  put(dedupTable, withKey(headerKey, metadata), "PK"),
                  put(dedupTable, withKey(hashKey, metadata), "PK"),
                  put(inboxTable, inboxAttributes, InboxAttributes.IDEMPOTENCY_KEY))
              .build());
      return new InboxWriteResult(true, null, null, proposalId, false);
    } catch (TransactionCanceledException e) {
      // A transaction conflict/throttle is not proof of duplication. Resolve only committed data.
      try {
        Map<String, AttributeValue> header = read(dedupTable, key(headerKey));
        if (!header.isEmpty()) {
          return technical(header, item);
        }
        Map<String, AttributeValue> business = read(dedupTable, key(hashKey));
        if (!business.isEmpty()) {
          return bindAlias(headerKey, hashKey, business, item);
        }
      } catch (SdkException failedRead) {
        throw unavailable(failedRead);
      }
      throw unavailable(e);
    } catch (SdkException e) {
      throw unavailable(e);
    }
  }

  private InboxWriteResult bindAlias(
      String headerKey, String hashKey, Map<String, AttributeValue> business, InboxItem item) {
    verifyTenant(business, item);
    Map<String, AttributeValue> alias = new HashMap<>(business);
    alias.put("requestFingerprint", s(item.fingerprints().request()));
    try {
      dynamo.transactWriteItems(
          TransactWriteItemsRequest.builder()
              .clientRequestToken(UUID.randomUUID().toString())
              .transactItems(
                  TransactWriteItem.builder()
                      .conditionCheck(
                          ConditionCheck.builder()
                              .tableName(dedupTable)
                              .key(key(hashKey))
                              .conditionExpression("proposalId = :proposal")
                              .expressionAttributeValues(
                                  Map.of(":proposal", business.get("proposalId")))
                              .build())
                      .build(),
                  put(dedupTable, withKey(headerKey, alias), "PK"))
              .build());
      return duplicate(business, true);
    } catch (TransactionCanceledException e) {
      Map<String, AttributeValue> winner = read(dedupTable, key(headerKey));
      if (!winner.isEmpty()) {
        return technical(winner, item);
      }
      throw unavailable(e);
    }
  }

  private static InboxWriteResult technical(Map<String, AttributeValue> header, InboxItem item) {
    verifyTenant(header, item);
    if (!item.fingerprints().request().equals(value(header, "requestFingerprint"))) {
      throw new IdempotencyMismatchException();
    }
    return duplicate(header, false);
  }

  private static void verifyTenant(Map<String, AttributeValue> record, InboxItem item) {
    if (!item.tenant().value().equals(value(record, "tenantId"))) {
      throw new IdempotencyMismatchException();
    }
  }

  private static InboxWriteResult duplicate(Map<String, AttributeValue> record, boolean semantic) {
    String proposalId = value(record, "proposalId");
    String trace = value(record, "tracingId");
    AttributeValue created = record.get("createdAt");
    if (proposalId == null || trace == null || created == null || created.n() == null) {
      throw unavailable(null);
    }
    return new InboxWriteResult(false, trace, Long.valueOf(created.n()), proposalId, semantic);
  }

  private Map<String, AttributeValue> read(String table, Map<String, AttributeValue> key) {
    return dynamo
        .getItem(GetItemRequest.builder().tableName(table).key(key).consistentRead(true).build())
        .item();
  }

  private static TransactWriteItem put(
      String table, Map<String, AttributeValue> item, String keyName) {
    return TransactWriteItem.builder()
        .put(
            Put.builder()
                .tableName(table)
                .item(item)
                .conditionExpression("attribute_not_exists(#pk)")
                .expressionAttributeNames(Map.of("#pk", keyName))
                .build())
        .build();
  }

  private static Map<String, AttributeValue> metadata(InboxItem item, String proposalId) {
    Map<String, AttributeValue> result =
        new HashMap<>(
            Map.of(
                "proposalId",
                s(proposalId),
                "tenantId",
                s(item.tenant().value()),
                "tracingId",
                s(item.tracingId()),
                "createdAt",
                n(item.createdAtEpochMillis()),
                "ttl",
                n(item.ttlEpochSeconds()),
                "requestFingerprint",
                s(item.fingerprints().request())));
    if (item.fingerprints().businessDate() != null) {
      result.put("businessDate", s(item.fingerprints().businessDate().toString()));
    }
    return result;
  }

  private static Map<String, AttributeValue> withKey(
      String pk, Map<String, AttributeValue> values) {
    Map<String, AttributeValue> result = new HashMap<>(values);
    result.putAll(key(pk));
    return result;
  }

  private static Map<String, AttributeValue> key(String pk) {
    return Map.of("PK", s(pk), "SK", s("METADATA"));
  }

  private static String value(Map<String, AttributeValue> record, String name) {
    return record.containsKey(name) ? record.get(name).s() : null;
  }

  private static AttributeValue s(String value) {
    return AttributeValue.builder().s(value).build();
  }

  private static AttributeValue n(long value) {
    return AttributeValue.builder().n(Long.toString(value)).build();
  }

  private static InboxUnavailableException unavailable(Throwable cause) {
    return new InboxUnavailableException("Business fingerprint store unavailable", cause);
  }
}
