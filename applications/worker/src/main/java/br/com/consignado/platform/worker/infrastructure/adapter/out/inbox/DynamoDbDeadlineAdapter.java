package br.com.consignado.platform.worker.infrastructure.adapter.out.inbox;

import br.com.consignado.platform.shared.contract.InboxAttributes;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.worker.application.port.out.DeadlineIndexPort;
import br.com.consignado.platform.worker.application.port.out.DecisionRecorderPort;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * Decisao e indice de prazos do SLO J2 no DurableInbox. A decisao e gravada uma unica vez ({@code
 * attribute_not_exists}); o fechamento remove {@code sloOpen} condicionalmente, entao cada proposta
 * conta uma vez mesmo com varias replicas do avaliador.
 */
public final class DynamoDbDeadlineAdapter implements DeadlineIndexPort, DecisionRecorderPort {

  private static final Logger LOG = System.getLogger(DynamoDbDeadlineAdapter.class.getName());

  private final DynamoDbClient dynamo;
  private final String tableName;

  public DynamoDbDeadlineAdapter(DynamoDbClient dynamo, String tableName) {
    this.dynamo = dynamo;
    this.tableName = tableName;
  }

  @Override
  public void recordDecision(IdempotencyKey key, boolean confirmed, Instant at) {
    String decision =
        confirmed ? InboxAttributes.DECISION_CONFIRMED : InboxAttributes.DECISION_BUSINESS_REJECTED;
    try {
      dynamo.updateItem(
          UpdateItemRequest.builder()
              .tableName(tableName)
              .key(key(key))
              .updateExpression("SET #decision = :decision, #decidedAt = :decidedAt")
              .conditionExpression("attribute_exists(#key) AND attribute_not_exists(#decision)")
              .expressionAttributeNames(
                  Map.of(
                      "#key", InboxAttributes.IDEMPOTENCY_KEY,
                      "#decision", InboxAttributes.DECISION,
                      "#decidedAt", InboxAttributes.DECIDED_AT))
              .expressionAttributeValues(
                  Map.of(
                      ":decision", AttributeValue.builder().s(decision).build(),
                      ":decidedAt",
                          AttributeValue.builder().n(Long.toString(at.toEpochMilli())).build()))
              .build());
    } catch (ConditionalCheckFailedException alreadyDecided) {
      // idempotente: reprocessamento nao altera a primeira decisao
    } catch (SdkException e) {
      LOG.log(Level.WARNING, "event=slo_decision_write_failed error={0}", e.getClass().getName());
      throw new IllegalStateException("Decisao da Inbox indisponivel; reentrega necessaria", e);
    }
  }

  @Override
  public List<OpenProposal> acceptedUpTo(Lane lane, long createdAtOrBefore, int limit) {
    List<OpenProposal> result = new ArrayList<>();
    Map<String, AttributeValue> start = null;
    do {
      QueryResponse response =
          dynamo.query(
              QueryRequest.builder()
                  .tableName(tableName)
                  .indexName(InboxAttributes.SLO_OPEN_INDEX)
                  .keyConditionExpression("#open = :lane AND #createdAt <= :cutoff")
                  .expressionAttributeNames(
                      Map.of(
                          "#open", InboxAttributes.SLO_OPEN,
                          "#createdAt", InboxAttributes.CREATED_AT))
                  .expressionAttributeValues(
                      Map.of(
                          ":lane",
                          AttributeValue.builder().s(lane.name().toLowerCase(Locale.ROOT)).build(),
                          ":cutoff",
                          AttributeValue.builder().n(Long.toString(createdAtOrBefore)).build()))
                  .limit(limit - result.size())
                  .exclusiveStartKey(start)
                  .build());
      for (Map<String, AttributeValue> item : response.items()) {
        AttributeValue decidedAt = item.get(InboxAttributes.DECIDED_AT);
        result.add(
            new OpenProposal(
                IdempotencyKey.parse(item.get(InboxAttributes.IDEMPOTENCY_KEY).s()),
                Long.parseLong(item.get(InboxAttributes.CREATED_AT).n()),
                decidedAt == null ? null : Long.parseLong(decidedAt.n())));
      }
      start = response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
    } while (start != null && !start.isEmpty() && result.size() < limit);
    return result;
  }

  @Override
  public boolean close(IdempotencyKey key, String outcome) {
    try {
      dynamo.updateItem(
          UpdateItemRequest.builder()
              .tableName(tableName)
              .key(key(key))
              .updateExpression("REMOVE #open SET #outcome = :outcome")
              .conditionExpression("attribute_exists(#open)")
              .expressionAttributeNames(
                  Map.of(
                      "#open", InboxAttributes.SLO_OPEN, "#outcome", InboxAttributes.SLO_OUTCOME))
              .expressionAttributeValues(
                  Map.of(":outcome", AttributeValue.builder().s(outcome).build()))
              .build());
      return true;
    } catch (ConditionalCheckFailedException alreadyClosed) {
      return false;
    }
  }

  private static Map<String, AttributeValue> key(IdempotencyKey key) {
    return Map.of(
        InboxAttributes.IDEMPOTENCY_KEY, AttributeValue.builder().s(key.toString()).build());
  }
}
