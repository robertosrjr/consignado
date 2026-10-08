package br.com.consignado.platform.dispatcher.lambda;

import br.com.consignado.platform.dispatcher.dispatch.StreamRecordView;
import br.com.consignado.platform.shared.contract.InboxAttributes;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import java.util.Map;

/** Converte registros do evento Lambda (DynamoDB Streams) para a visao neutra do despacho. */
public final class StreamRecordMapper {

  private StreamRecordMapper() {}

  public static StreamRecordView toView(DynamodbStreamRecord record) {
    Map<String, AttributeValue> image =
        record.getDynamodb() == null ? null : record.getDynamodb().getNewImage();
    String sequence =
        record.getDynamodb() == null ? null : record.getDynamodb().getSequenceNumber();
    if (image == null) {
      return new StreamRecordView(
          record.getEventName(), sequence, null, null, null, null, null, null, null);
    }
    return new StreamRecordView(
        record.getEventName(),
        sequence,
        string(image, InboxAttributes.IDEMPOTENCY_KEY),
        string(image, InboxAttributes.TENANT_ID),
        string(image, InboxAttributes.CONVENIO_TYPE),
        bool(image, InboxAttributes.PORTABILITY),
        number(image, InboxAttributes.AMOUNT),
        string(image, InboxAttributes.TRACING_ID),
        string(image, InboxAttributes.STATUS));
  }

  private static String string(Map<String, AttributeValue> image, String name) {
    AttributeValue value = image.get(name);
    return value == null ? null : value.getS();
  }

  private static String number(Map<String, AttributeValue> image, String name) {
    AttributeValue value = image.get(name);
    return value == null ? null : value.getN();
  }

  private static Boolean bool(Map<String, AttributeValue> image, String name) {
    AttributeValue value = image.get(name);
    return value == null ? null : value.getBOOL();
  }
}
