package br.com.consignado.platform.dispatcher.aws;

import br.com.consignado.platform.dispatcher.dispatch.PendingDispatchReader;
import br.com.consignado.platform.dispatcher.dispatch.StreamRecordView;
import br.com.consignado.platform.shared.contract.InboxAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

/**
 * Le o indice esparso {@code dispatchPending-createdAt}: somente itens ainda nao despachados, com
 * os atributos de roteamento projetados (o payload cifrado nao e lido).
 */
public final class DynamoPendingDispatchReader implements PendingDispatchReader {

  private final DynamoDbClient dynamo;
  private final String tableName;

  public DynamoPendingDispatchReader(DynamoDbClient dynamo, String tableName) {
    this.dynamo = dynamo;
    this.tableName = tableName;
  }

  @Override
  public List<StreamRecordView> pendingCreatedUpTo(long createdAtOrBefore, int limit) {
    List<StreamRecordView> result = new ArrayList<>();
    Map<String, AttributeValue> start = null;
    do {
      QueryResponse response =
          dynamo.query(
              QueryRequest.builder()
                  .tableName(tableName)
                  .indexName(InboxAttributes.DISPATCH_PENDING_INDEX)
                  .keyConditionExpression("#pending = :pending AND #createdAt <= :cutoff")
                  .expressionAttributeNames(
                      Map.of(
                          "#pending", InboxAttributes.DISPATCH_PENDING,
                          "#createdAt", InboxAttributes.CREATED_AT))
                  .expressionAttributeValues(
                      Map.of(
                          ":pending",
                          AttributeValue.builder()
                              .s(InboxAttributes.DISPATCH_PENDING_VALUE)
                              .build(),
                          ":cutoff",
                          AttributeValue.builder().n(Long.toString(createdAtOrBefore)).build()))
                  .limit(limit - result.size())
                  .exclusiveStartKey(start)
                  .build());
      for (Map<String, AttributeValue> item : response.items()) {
        AttributeValue portability = item.get(InboxAttributes.PORTABILITY);
        AttributeValue amount = item.get(InboxAttributes.AMOUNT);
        result.add(
            new StreamRecordView(
                "INSERT",
                "sweeper",
                text(item, InboxAttributes.IDEMPOTENCY_KEY),
                text(item, InboxAttributes.TENANT_ID),
                text(item, InboxAttributes.CONVENIO_TYPE),
                portability == null ? null : portability.bool(),
                amount == null ? null : amount.n(),
                text(item, InboxAttributes.TRACING_ID),
                text(item, InboxAttributes.STATUS)));
      }
      start = response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
    } while (start != null && !start.isEmpty() && result.size() < limit);
    return result;
  }

  private static String text(Map<String, AttributeValue> item, String name) {
    AttributeValue value = item.get(name);
    return value == null ? null : value.s();
  }
}
