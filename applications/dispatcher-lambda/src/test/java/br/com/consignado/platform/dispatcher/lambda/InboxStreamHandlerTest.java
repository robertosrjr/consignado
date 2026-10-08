package br.com.consignado.platform.dispatcher.lambda;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.dispatcher.dispatch.BatchDispatcher;
import br.com.consignado.platform.dispatcher.dispatch.DispatchService;
import br.com.consignado.platform.dispatcher.dispatch.InboxStatusUpdater;
import br.com.consignado.platform.dispatcher.dispatch.QueuePublisher;
import br.com.consignado.platform.dispatcher.dispatch.StreamRecordView;
import br.com.consignado.platform.dispatcher.metrics.EmfMetrics;
import br.com.consignado.platform.dispatcher.routing.LaneRouter;
import br.com.consignado.platform.dispatcher.routing.PriorityRules;
import br.com.consignado.platform.dispatcher.routing.PriorityScorer;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.contract.ProposalMessage;
import br.com.consignado.platform.shared.vo.Money;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InboxStreamHandlerTest {

  private final List<Lane> lanes = new ArrayList<>();
  private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();

  private InboxStreamHandler handler(boolean queueDown) {
    QueuePublisher publisher =
        (lane, message, tracingId) -> {
          if (queueDown) {
            throw new IllegalStateException("fila fora do ar");
          }
          lanes.add(lane);
        };
    InboxStatusUpdater updater = (key, score) -> {};
    PriorityRules rules = PriorityRules.withDefaults(Set.of(), Money.of("50000.00"));
    BatchDispatcher dispatcher =
        new BatchDispatcher(
            new DispatchService(
                new PriorityScorer(rules),
                new LaneRouter(rules.fastThreshold()),
                publisher,
                updater));
    EmfMetrics metrics =
        new EmfMetrics(
            new PrintStream(stdout, true, StandardCharsets.UTF_8),
            Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC));
    return new InboxStreamHandler(dispatcher, metrics);
  }

  private static DynamodbStreamRecord record(
      String event, String sequence, String key, boolean portability) {
    Map<String, AttributeValue> image =
        Map.of(
            "idempotencyKey", new AttributeValue().withS(key),
            "tenantId", new AttributeValue().withS("banco-a"),
            "convenioType", new AttributeValue().withS("INSS"),
            "portability", new AttributeValue().withBOOL(portability),
            "amount", new AttributeValue().withN("1000.00"),
            "tracingId", new AttributeValue().withS("trace-" + sequence),
            "status", new AttributeValue().withS("RECEIVED"));
    DynamodbStreamRecord record = new DynamodbStreamRecord();
    record.setEventName(event);
    StreamRecord dynamodb = new StreamRecord();
    dynamodb.setSequenceNumber(sequence);
    dynamodb.setNewImage(image);
    record.setDynamodb(dynamodb);
    return record;
  }

  private static DynamodbEvent event(DynamodbStreamRecord... records) {
    DynamodbEvent event = new DynamodbEvent();
    event.setRecords(List.of(records));
    return event;
  }

  @Test
  void should_dispatch_inserts_and_report_no_failures() {
    StreamsEventResponse response =
        handler(false)
            .handleRequest(
                event(
                    record("INSERT", "100", "e3b0c442-98fc-11ee-b9d1-0242ac120002", true),
                    record("INSERT", "101", "0b0c0d0e-0000-4000-8000-000000000001", false)),
                null);

    assertThat(response.getBatchItemFailures()).isEmpty();
    assertThat(lanes).containsExactly(Lane.FAST, Lane.STANDARD);
  }

  @Test
  void should_return_the_failing_sequence_numbers_as_batch_item_failures() {
    StreamsEventResponse response =
        handler(false)
            .handleRequest(
                event(
                    record("INSERT", "200", "e3b0c442-98fc-11ee-b9d1-0242ac120002", true),
                    record("INSERT", "201", "nao-uuid", true)),
                null);

    assertThat(response.getBatchItemFailures())
        .extracting(StreamsEventResponse.BatchItemFailure::getItemIdentifier)
        .containsExactly("201");
  }

  @Test
  void should_fail_every_record_when_the_queue_is_down_so_the_stream_retries() {
    StreamsEventResponse response =
        handler(true)
            .handleRequest(
                event(record("INSERT", "300", "e3b0c442-98fc-11ee-b9d1-0242ac120002", true)), null);

    assertThat(response.getBatchItemFailures()).hasSize(1);
  }

  @Test
  void should_skip_removals_without_failing() {
    StreamsEventResponse response =
        handler(false)
            .handleRequest(
                event(record("REMOVE", "400", "e3b0c442-98fc-11ee-b9d1-0242ac120002", true)), null);

    assertThat(response.getBatchItemFailures()).isEmpty();
    assertThat(lanes).isEmpty();
  }

  @Test
  void should_emit_emf_metrics_without_any_proposal_data() {
    handler(false)
        .handleRequest(
            event(record("INSERT", "500", "e3b0c442-98fc-11ee-b9d1-0242ac120002", true)), null);

    String metrics = stdout.toString(StandardCharsets.UTF_8);
    assertThat(metrics)
        .contains("\"Namespace\":\"ConsignadoPoc/Dispatcher\"")
        .contains("\"RecordsDispatched\":1")
        .contains("\"Lane\":\"FAST\"")
        .contains("\"RecordsFailed\":0")
        .doesNotContain("e3b0c442")
        .doesNotContain("banco-a");
  }

  @Test
  void should_map_a_stream_record_into_the_neutral_view() {
    StreamRecordView view =
        StreamRecordMapper.toView(
            record("INSERT", "600", "e3b0c442-98fc-11ee-b9d1-0242ac120002", true));

    assertThat(view.isInsert()).isTrue();
    assertThat(view.sequenceNumber()).isEqualTo("600");
    assertThat(view.tenantId()).isEqualTo("banco-a");
    assertThat(view.portability()).isTrue();
    assertThat(view.amount()).isEqualTo("1000.00");
    assertThat(view.status()).isEqualTo("RECEIVED");
  }

  @Test
  void should_map_a_record_without_image_to_an_empty_view() {
    DynamodbStreamRecord withoutImage = new DynamodbStreamRecord();
    withoutImage.setEventName("REMOVE");
    withoutImage.setDynamodb(new StreamRecord());
    DynamodbStreamRecord withoutDynamo = new DynamodbStreamRecord();
    withoutDynamo.setEventName("REMOVE");

    assertThat(StreamRecordMapper.toView(withoutImage).idempotencyKey()).isNull();
    assertThat(StreamRecordMapper.toView(withoutDynamo).sequenceNumber()).isNull();
  }

  @Test
  void should_keep_message_type_independent_of_the_lambda_event_types() {
    assertThat(ProposalMessage.class.getPackageName()).doesNotContain("amazonaws");
  }
}
