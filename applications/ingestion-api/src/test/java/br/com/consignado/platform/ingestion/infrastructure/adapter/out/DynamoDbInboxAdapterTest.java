package br.com.consignado.platform.ingestion.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.ingestion.application.port.out.InboxItem;
import br.com.consignado.platform.ingestion.application.port.out.InboxWriteResult;
import br.com.consignado.platform.ingestion.domain.InboxUnavailableException;
import br.com.consignado.platform.ingestion.infrastructure.adapter.out.persistence.DynamoDbInboxAdapter;
import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.contract.InboxAttributes;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.shared.vo.TenantId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure;

/** Mapeamento do adaptador contra um cliente falso (a semantica real e verificada nos *IT). */
class DynamoDbInboxAdapterTest {

  /** Cliente falso: grava a ultima requisicao e simula sucesso, duplicidade ou falha. */
  private static final class FakeDynamo implements DynamoDbClient {
    PutItemRequest last;
    RuntimeException toThrow;

    @Override
    public PutItemResponse putItem(PutItemRequest request) {
      last = request;
      if (toThrow != null) {
        throw toThrow;
      }
      return PutItemResponse.builder().build();
    }

    @Override
    public String serviceName() {
      return "dynamodb";
    }

    @Override
    public void close() {}
  }

  private final FakeDynamo dynamo = new FakeDynamo();
  private final DynamoDbInboxAdapter adapter = new DynamoDbInboxAdapter(dynamo, "DurableInbox");

  private static InboxItem item() {
    return new InboxItem(
        IdempotencyKey.parse("e3b0c442-98fc-11ee-b9d1-0242ac120002"),
        new TenantId("banco-a"),
        ConvenioType.ESOCIAL_CLT,
        true,
        Money.of("15000.5"),
        "v1.a.b.c",
        1_700_000_000_000L,
        1_700_604_800L,
        "trace-1");
  }

  @Test
  void should_write_all_attributes_with_a_conditional_expression() {
    InboxWriteResult result = adapter.putIfAbsent(item());

    assertThat(result.created()).isTrue();
    PutItemRequest request = dynamo.last;
    assertThat(request.tableName()).isEqualTo("DurableInbox");
    assertThat(request.conditionExpression()).isEqualTo("attribute_not_exists(#key)");
    assertThat(request.expressionAttributeNames())
        .containsEntry("#key", InboxAttributes.IDEMPOTENCY_KEY);
    assertThat(request.returnValuesOnConditionCheckFailure())
        .isEqualTo(ReturnValuesOnConditionCheckFailure.ALL_OLD);
    Map<String, AttributeValue> attributes = request.item();
    assertThat(attributes.get(InboxAttributes.IDEMPOTENCY_KEY).s())
        .isEqualTo("e3b0c442-98fc-11ee-b9d1-0242ac120002");
    assertThat(attributes.get(InboxAttributes.TENANT_ID).s()).isEqualTo("banco-a");
    assertThat(attributes.get(InboxAttributes.CONVENIO_TYPE).s()).isEqualTo("ESOCIAL_CLT");
    assertThat(attributes.get(InboxAttributes.PORTABILITY).bool()).isTrue();
    assertThat(attributes.get(InboxAttributes.AMOUNT).n()).isEqualTo("15000.50");
    assertThat(attributes.get(InboxAttributes.STATUS).s()).isEqualTo("RECEIVED");
    assertThat(attributes.get(InboxAttributes.CREATED_AT).n()).isEqualTo("1700000000000");
    assertThat(attributes.get(InboxAttributes.TTL).n()).isEqualTo("1700604800");
    assertThat(attributes.get(InboxAttributes.TRACING_ID).s()).isEqualTo("trace-1");
    assertThat(attributes.get(InboxAttributes.PAYLOAD).s()).isEqualTo("v1.a.b.c");
  }

  @Test
  void should_return_the_original_tracing_id_and_time_on_a_conditional_failure() {
    dynamo.toThrow =
        ConditionalCheckFailedException.builder()
            .message("condicao falhou")
            .item(
                Map.of(
                    InboxAttributes.TRACING_ID, AttributeValue.builder().s("trace-0").build(),
                    InboxAttributes.CREATED_AT,
                        AttributeValue.builder().n("1690000000000").build()))
            .build();

    InboxWriteResult result = adapter.putIfAbsent(item());

    assertThat(result.created()).isFalse();
    assertThat(result.existingTracingId()).isEqualTo("trace-0");
    assertThat(result.existingCreatedAt()).isEqualTo(1_690_000_000_000L);
  }

  @Test
  void should_still_report_a_duplicate_when_the_old_item_is_not_returned() {
    dynamo.toThrow = ConditionalCheckFailedException.builder().message("x").build();

    InboxWriteResult result = adapter.putIfAbsent(item());

    assertThat(result.created()).isFalse();
    assertThat(result.existingTracingId()).isNull();
    assertThat(result.existingCreatedAt()).isNull();
  }

  @Test
  void should_translate_sdk_failures_into_an_unavailable_inbox_without_leaking_details() {
    dynamo.toThrow = SdkClientException.create("connection refused to 10.0.0.1");

    assertThatThrownBy(() -> adapter.putIfAbsent(item()))
        .isInstanceOf(InboxUnavailableException.class)
        .hasMessageNotContaining("10.0.0.1");
  }
}
