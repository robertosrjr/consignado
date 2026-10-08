package br.com.consignado.platform.worker.infrastructure.adapter.out.inbox;

import br.com.consignado.platform.shared.contract.InboxAttributes;
import br.com.consignado.platform.shared.crypto.PayloadEnvelope;
import br.com.consignado.platform.shared.payload.InboxPayload;
import br.com.consignado.platform.shared.payload.InboxPayloadCodec;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;
import br.com.consignado.platform.worker.application.port.out.InboxReadPort;
import br.com.consignado.platform.worker.application.port.out.InboxRecord;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

/**
 * Le o item completo do DurableInbox (leitura consistente) e decifra o payload; o CPF so existe em
 * memoria, durante o processamento. Falhas de infraestrutura propagam (a mensagem volta a fila).
 */
public final class DynamoDbInboxReadAdapter implements InboxReadPort {

  private final DynamoDbClient dynamo;
  private final String tableName;
  private final PayloadEnvelope envelope;

  public DynamoDbInboxReadAdapter(
      DynamoDbClient dynamo, String tableName, PayloadEnvelope envelope) {
    this.dynamo = dynamo;
    this.tableName = tableName;
    this.envelope = envelope;
  }

  @Override
  public Optional<InboxRecord> load(IdempotencyKey key) {
    GetItemResponse response;
    try {
      response =
          dynamo.getItem(
              GetItemRequest.builder()
                  .tableName(tableName)
                  .consistentRead(true)
                  .key(
                      Map.of(
                          InboxAttributes.IDEMPOTENCY_KEY,
                          AttributeValue.builder().s(key.toString()).build()))
                  .build());
    } catch (SdkException e) {
      throw new IllegalStateException("DurableInbox indisponivel para leitura", e);
    }
    if (!response.hasItem() || response.item().isEmpty()) {
      return Optional.empty();
    }
    Map<String, AttributeValue> item = response.item();
    String protectedPayload = text(item, InboxAttributes.PAYLOAD);
    String tenant = text(item, InboxAttributes.TENANT_ID);
    if (protectedPayload == null || tenant == null) {
      throw new IllegalStateException("Item do DurableInbox incompleto");
    }
    InboxPayload payload = InboxPayloadCodec.fromBytes(envelope.unprotect(protectedPayload));
    return Optional.of(
        new InboxRecord(
            key,
            new TenantId(tenant),
            payload,
            text(item, InboxAttributes.TRACING_ID),
            number(item, InboxAttributes.CREATED_AT)));
  }

  private static Long number(Map<String, AttributeValue> item, String name) {
    AttributeValue value = item.get(name);
    return value == null || value.n() == null ? null : Long.valueOf(value.n());
  }

  private static String text(Map<String, AttributeValue> item, String name) {
    AttributeValue value = item.get(name);
    return value == null ? null : value.s();
  }
}
