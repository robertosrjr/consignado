package br.com.consignado.platform.worker.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.contract.InboxAttributes;
import br.com.consignado.platform.shared.crypto.LocalDataKeyProvider;
import br.com.consignado.platform.shared.crypto.PayloadEnvelope;
import br.com.consignado.platform.shared.crypto.PayloadProtectionException;
import br.com.consignado.platform.shared.payload.InboxPayload;
import br.com.consignado.platform.shared.payload.InboxPayloadCodec;
import br.com.consignado.platform.shared.vo.Cpf;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.worker.application.port.out.InboxRecord;
import br.com.consignado.platform.worker.infrastructure.adapter.out.inbox.DynamoDbInboxReadAdapter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

class DynamoDbInboxReadAdapterTest {

  private static final IdempotencyKey KEY =
      IdempotencyKey.parse("e3b0c442-98fc-11ee-b9d1-0242ac120002");

  private static final class FakeDynamo implements DynamoDbClient {
    GetItemRequest last;
    Map<String, AttributeValue> item;
    RuntimeException failure;

    @Override
    public GetItemResponse getItem(GetItemRequest request) {
      last = request;
      if (failure != null) {
        throw failure;
      }
      return item == null
          ? GetItemResponse.builder().build()
          : GetItemResponse.builder().item(item).build();
    }

    @Override
    public String serviceName() {
      return "dynamodb";
    }

    @Override
    public void close() {}
  }

  private final PayloadEnvelope envelope =
      new PayloadEnvelope(new LocalDataKeyProvider(new byte[32]));
  private final FakeDynamo dynamo = new FakeDynamo();
  private final DynamoDbInboxReadAdapter adapter =
      new DynamoDbInboxReadAdapter(dynamo, "DurableInbox", envelope);

  private Map<String, AttributeValue> validItem(String protectedPayload) {
    Map<String, AttributeValue> item = new HashMap<>();
    item.put(InboxAttributes.IDEMPOTENCY_KEY, AttributeValue.builder().s(KEY.toString()).build());
    item.put(InboxAttributes.TENANT_ID, AttributeValue.builder().s("banco-a").build());
    item.put(InboxAttributes.PAYLOAD, AttributeValue.builder().s(protectedPayload).build());
    item.put(InboxAttributes.TRACING_ID, AttributeValue.builder().s("trace-1").build());
    return item;
  }

  private String protectedPayload() {
    return envelope.protect(
        InboxPayloadCodec.toBytes(
            InboxPayload.of(
                new Cpf("52998224725"), ConvenioType.ESOCIAL_CLT, true, Money.of("2500.75"), 24)));
  }

  @Test
  void should_load_and_decrypt_the_item_using_a_consistent_read() {
    dynamo.item = validItem(protectedPayload());

    Optional<InboxRecord> record = adapter.load(KEY);

    assertThat(record).isPresent();
    assertThat(record.get().tenant().value()).isEqualTo("banco-a");
    assertThat(record.get().tracingId()).isEqualTo("trace-1");
    assertThat(record.get().payload().cpf().digits()).isEqualTo("52998224725");
    assertThat(record.get().payload().convenio()).isEqualTo(ConvenioType.ESOCIAL_CLT);
    assertThat(record.get().payload().installments()).isEqualTo(24);
    assertThat(dynamo.last.consistentRead()).isTrue();
    assertThat(dynamo.last.tableName()).isEqualTo("DurableInbox");
    assertThat(dynamo.last.key().get(InboxAttributes.IDEMPOTENCY_KEY).s())
        .isEqualTo(KEY.toString());
  }

  @Test
  void should_return_empty_when_the_item_does_not_exist() {
    dynamo.item = null;

    assertThat(adapter.load(KEY)).isEmpty();
  }

  @Test
  void should_fail_for_an_incomplete_item() {
    Map<String, AttributeValue> item = validItem(protectedPayload());
    item.remove(InboxAttributes.PAYLOAD);
    dynamo.item = item;

    assertThatThrownBy(() -> adapter.load(KEY)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void should_fail_when_the_payload_was_tampered_with() {
    dynamo.item = validItem(protectedPayload().replaceAll(".$", "A"));

    assertThatThrownBy(() -> adapter.load(KEY)).isInstanceOf(PayloadProtectionException.class);
  }

  @Test
  void should_translate_infrastructure_failures_without_leaking_details() {
    dynamo.failure = SdkClientException.create("timeout calling 10.0.0.9");

    assertThatThrownBy(() -> adapter.load(KEY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining("10.0.0.9");
  }

  @Test
  void should_never_print_the_payload_in_the_record_to_string() {
    dynamo.item = validItem(protectedPayload());

    assertThat(adapter.load(KEY).orElseThrow().toString()).doesNotContain("52998224725");
  }
}
