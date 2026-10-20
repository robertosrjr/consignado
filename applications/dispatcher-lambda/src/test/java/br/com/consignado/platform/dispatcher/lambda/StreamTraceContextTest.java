package br.com.consignado.platform.dispatcher.lambda;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StreamTraceContextTest {
  @Test
  void should_preserve_w3c_headers_from_inbox_image_but_exclude_baggage() {
    String parent = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";
    var record = new DynamodbEvent.DynamodbStreamRecord();
    record.setEventName("INSERT");
    var stream = new StreamRecord();
    stream.setNewImage(
        Map.of(
            "traceparent",
            new AttributeValue().withS(parent),
            "tracestate",
            new AttributeValue().withS("vendor=value"),
            "baggage",
            new AttributeValue().withS("cpf=secret")));
    record.setDynamodb(stream);
    assertThat(StreamRecordMapper.toView(record).traceContext())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("traceparent", parent, "tracestate", "vendor=value"));
  }
}
