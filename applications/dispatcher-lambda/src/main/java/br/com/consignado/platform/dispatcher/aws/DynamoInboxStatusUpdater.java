package br.com.consignado.platform.dispatcher.aws;

import br.com.consignado.platform.dispatcher.dispatch.InboxStatusUpdater;
import br.com.consignado.platform.shared.contract.InboxAttributes;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import java.util.Map;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * RECEIVED para DISPATCHED de forma condicional: reprocessar o mesmo registro do Stream nao regride
 * nem sobrescreve o estado de itens ja despachados.
 */
public final class DynamoInboxStatusUpdater implements InboxStatusUpdater {

  private final DynamoDbClient dynamo;
  private final String tableName;

  public DynamoInboxStatusUpdater(DynamoDbClient dynamo, String tableName) {
    this.dynamo = dynamo;
    this.tableName = tableName;
  }

  @Override
  public void markDispatched(IdempotencyKey key, int priorityScore) {
    UpdateItemRequest request =
        UpdateItemRequest.builder()
            .tableName(tableName)
            .key(
                Map.of(
                    InboxAttributes.IDEMPOTENCY_KEY,
                    AttributeValue.builder().s(key.toString()).build()))
            .updateExpression("SET #status = :dispatched, #score = :score")
            .conditionExpression("#status = :received")
            .expressionAttributeNames(
                Map.of(
                    "#status", InboxAttributes.STATUS,
                    "#score", InboxAttributes.PRIORITY_SCORE))
            .expressionAttributeValues(
                Map.of(
                    ":dispatched",
                        AttributeValue.builder().s(InboxAttributes.STATUS_DISPATCHED).build(),
                    ":received",
                        AttributeValue.builder().s(InboxAttributes.STATUS_RECEIVED).build(),
                    ":score", AttributeValue.builder().n(Integer.toString(priorityScore)).build()))
            .build();
    try {
      dynamo.updateItem(request);
    } catch (ConditionalCheckFailedException alreadyDispatched) {
      // idempotente: o item ja avancou de estado
    } catch (SdkException e) {
      throw new IllegalStateException("Falha ao atualizar o DurableInbox", e);
    }
  }
}
