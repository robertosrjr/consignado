package br.com.consignado.platform.ingestion.infrastructure.adapter.out.persistence;

import br.com.consignado.platform.ingestion.application.port.out.DurableInboxPort;
import br.com.consignado.platform.ingestion.application.port.out.InboxItem;
import br.com.consignado.platform.ingestion.application.port.out.InboxWriteResult;
import br.com.consignado.platform.ingestion.domain.InboxUnavailableException;
import br.com.consignado.platform.shared.contract.InboxAttributes;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure;

/**
 * Escrita condicional atomica no DynamoDB ({@code attribute_not_exists(idempotencyKey)}). Em caso
 * de duplicidade, o proprio erro devolve o item original (ALL_OLD), sem leitura extra.
 */
public final class DynamoDbInboxAdapter implements DurableInboxPort {

  private final DynamoDbClient dynamo;
  private final String tableName;

  public DynamoDbInboxAdapter(DynamoDbClient dynamo, String tableName) {
    this.dynamo = dynamo;
    this.tableName = tableName;
  }

  @Override
  public InboxWriteResult putIfAbsent(InboxItem item) {
    PutItemRequest request =
        PutItemRequest.builder()
            .tableName(tableName)
            .item(toAttributes(item))
            .conditionExpression("attribute_not_exists(#key)")
            .expressionAttributeNames(Map.of("#key", InboxAttributes.IDEMPOTENCY_KEY))
            .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
            .build();
    try {
      dynamo.putItem(request);
      return InboxWriteResult.createdNew();
    } catch (ConditionalCheckFailedException duplicate) {
      return fromExisting(duplicate.item());
    } catch (SdkException e) {
      throw new InboxUnavailableException("DurableInbox indisponivel", e);
    }
  }

  private static InboxWriteResult fromExisting(Map<String, AttributeValue> existing) {
    String tracingId = null;
    Long createdAt = null;
    if (existing != null) {
      AttributeValue tracing = existing.get(InboxAttributes.TRACING_ID);
      AttributeValue created = existing.get(InboxAttributes.CREATED_AT);
      tracingId = tracing != null ? tracing.s() : null;
      createdAt = created != null && created.n() != null ? Long.valueOf(created.n()) : null;
    }
    return InboxWriteResult.alreadyExists(tracingId, createdAt);
  }

  private static Map<String, AttributeValue> toAttributes(InboxItem item) {
    Map<String, AttributeValue> attributes = new HashMap<>();
    attributes.put(InboxAttributes.IDEMPOTENCY_KEY, s(item.idempotencyKey().toString()));
    attributes.put(InboxAttributes.TENANT_ID, s(item.tenant().value()));
    attributes.put(InboxAttributes.CONVENIO_TYPE, s(item.convenio().name()));
    attributes.put(
        InboxAttributes.PORTABILITY, AttributeValue.builder().bool(item.portability()).build());
    attributes.put(InboxAttributes.AMOUNT, n(item.amount().amount().toPlainString()));
    attributes.put(InboxAttributes.PAYLOAD, s(item.protectedPayload()));
    attributes.put(InboxAttributes.STATUS, s(InboxAttributes.STATUS_RECEIVED));
    attributes.put(InboxAttributes.CREATED_AT, n(Long.toString(item.createdAtEpochMillis())));
    attributes.put(InboxAttributes.TTL, n(Long.toString(item.ttlEpochSeconds())));
    attributes.put(InboxAttributes.TRACING_ID, s(item.tracingId()));
    return attributes;
  }

  private static AttributeValue s(String value) {
    return AttributeValue.builder().s(value).build();
  }

  private static AttributeValue n(String value) {
    return AttributeValue.builder().n(value).build();
  }
}
