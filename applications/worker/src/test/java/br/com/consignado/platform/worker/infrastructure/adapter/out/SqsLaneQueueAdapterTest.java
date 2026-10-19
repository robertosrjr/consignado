package br.com.consignado.platform.worker.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.contract.ProposalMessage;
import br.com.consignado.platform.shared.contract.ProposalMessageCodec;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;
import br.com.consignado.platform.worker.application.port.out.QueuedMessage;
import br.com.consignado.platform.worker.infrastructure.adapter.out.messaging.SqsLaneQueueAdapter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

class SqsLaneQueueAdapterTest {
  @Test
  void should_restore_each_message_context_inside_processing_thread_and_create_fresh_retry_spans()
      throws Exception {
    try (var sdk =
        br.com.consignado.platform.telemetry.StandaloneTelemetry.create("test", Map.of())) {
      br.com.consignado.platform.telemetry.Telemetry.configure(sdk);
      String trace = "0123456789abcdef0123456789abcdef";
      var attributes =
          Map.of(
              "traceparent",
              software.amazon.awssdk.services.sqs.model.MessageAttributeValue.builder()
                  .dataType("String")
                  .stringValue("00-" + trace + "-0123456789abcdef-01")
                  .build());
      var messages = new ConcurrentLinkedDeque<Message>();
      messages.add(
          Message.builder()
              .body(ProposalMessageCodec.toJson(message(Lane.FAST, 1)))
              .receiptHandle("receipt")
              .messageAttributes(attributes)
              .build());
      sqs.queues.put(FAST_URL, messages);
      var queue = adapter();
      await(() -> queue.hasMessages(Lane.FAST));
      var queued = queue.poll(Lane.FAST).orElseThrow();
      var spanIds = new ArrayList<String>();
      try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
        for (int attempt = 0; attempt < 2; attempt++) {
          executor
              .submit(
                  queued.processingTask(
                      () -> {
                        var context = io.opentelemetry.api.trace.Span.current().getSpanContext();
                        assertThat(context.getTraceId()).isEqualTo(trace);
                        assertThat(org.slf4j.MDC.get("traceId")).isEqualTo(trace);
                        spanIds.add(context.getSpanId());
                      }))
              .get();
        }
        executor
            .submit(
                () ->
                    assertThat(io.opentelemetry.api.trace.Span.current().getSpanContext().isValid())
                        .isFalse())
            .get();
      }
      assertThat(spanIds).doesNotHaveDuplicates();
    } finally {
      br.com.consignado.platform.telemetry.Telemetry.configure(
          io.opentelemetry.api.OpenTelemetry.noop());
    }
  }

  private static final String FAST_URL = "http://sqs/fast";
  private static final String STANDARD_URL = "http://sqs/standard";

  private static final class FakeSqs implements SqsClient {
    final Map<String, Deque<Message>> queues = new ConcurrentHashMap<>();
    final List<DeleteMessageRequest> deletes = Collections.synchronizedList(new ArrayList<>());
    final List<ChangeMessageVisibilityRequest> visibilities =
        Collections.synchronizedList(new ArrayList<>());
    volatile boolean failReceive;
    volatile boolean failDelete;
    volatile String depthValue = "42";
    volatile boolean failDepth;

    void enqueue(String url, String body, String receiveCount) {
      Message.Builder message = Message.builder().body(body).receiptHandle("h-" + body.hashCode());
      if (receiveCount != null) {
        message.attributes(
            Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, receiveCount));
      }
      queues.computeIfAbsent(url, u -> new ConcurrentLinkedDeque<>()).add(message.build());
    }

    @Override
    public ReceiveMessageResponse receiveMessage(ReceiveMessageRequest request) {
      if (failReceive) {
        throw SdkClientException.create("falha de rede");
      }
      Deque<Message> queue =
          queues.computeIfAbsent(request.queueUrl(), u -> new ConcurrentLinkedDeque<>());
      List<Message> batch = new ArrayList<>();
      Message next;
      while (batch.size() < request.maxNumberOfMessages() && (next = queue.poll()) != null) {
        batch.add(next);
      }
      if (batch.isEmpty()) {
        try {
          Thread.sleep(15);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      return ReceiveMessageResponse.builder().messages(batch).build();
    }

    @Override
    public DeleteMessageResponse deleteMessage(DeleteMessageRequest request) {
      if (failDelete) {
        throw SdkClientException.create("falha");
      }
      deletes.add(request);
      return DeleteMessageResponse.builder().build();
    }

    @Override
    public ChangeMessageVisibilityResponse changeMessageVisibility(
        ChangeMessageVisibilityRequest request) {
      visibilities.add(request);
      return ChangeMessageVisibilityResponse.builder().build();
    }

    @Override
    public GetQueueAttributesResponse getQueueAttributes(GetQueueAttributesRequest request) {
      if (failDepth) {
        throw SdkClientException.create("falha");
      }
      return GetQueueAttributesResponse.builder()
          .attributes(Map.of(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, depthValue))
          .build();
    }

    @Override
    public String serviceName() {
      return "sqs";
    }

    @Override
    public void close() {}
  }

  private final FakeSqs sqs = new FakeSqs();
  private SqsLaneQueueAdapter adapter;

  private SqsLaneQueueAdapter adapter() {
    adapter = new SqsLaneQueueAdapter(sqs, FAST_URL, STANDARD_URL, 20, 1, 30);
    adapter.start();
    return adapter;
  }

  @AfterEach
  void stop() {
    if (adapter != null) {
      adapter.close();
    }
  }

  private static ProposalMessage message(Lane lane, long id) {
    return ProposalMessage.of(
        new IdempotencyKey(new java.util.UUID(0, id)), new TenantId("banco-a"), lane, 10);
  }

  private static void await(BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertThat(condition.getAsBoolean()).as("condicao atingida no prazo").isTrue();
  }

  @Test
  void should_prefetch_each_lane_from_its_own_queue_with_the_receive_count() throws Exception {
    sqs.enqueue(FAST_URL, ProposalMessageCodec.toJson(message(Lane.FAST, 1)), "2");
    sqs.enqueue(STANDARD_URL, ProposalMessageCodec.toJson(message(Lane.STANDARD, 2)), null);
    SqsLaneQueueAdapter queue = adapter();

    await(() -> queue.hasMessages(Lane.FAST) && queue.hasMessages(Lane.STANDARD));

    QueuedMessage fast = queue.poll(Lane.FAST).orElseThrow();
    QueuedMessage standard = queue.poll(Lane.STANDARD).orElseThrow();
    assertThat(fast.lane()).isEqualTo(Lane.FAST);
    assertThat(fast.message()).isEqualTo(message(Lane.FAST, 1));
    assertThat(fast.receiveCount()).isEqualTo(2);
    assertThat(standard.receiveCount()).isEqualTo(1);
    assertThat(queue.poll(Lane.FAST)).isEmpty();
  }

  @Test
  void should_skip_poison_messages_without_deleting_them_so_the_redrive_moves_them_to_the_dlq()
      throws Exception {
    sqs.enqueue(FAST_URL, "isto-nao-e-json", "1");
    sqs.enqueue(FAST_URL, ProposalMessageCodec.toJson(message(Lane.FAST, 3)), "1");
    SqsLaneQueueAdapter queue = adapter();

    await(() -> queue.hasMessages(Lane.FAST));

    assertThat(queue.poll(Lane.FAST).orElseThrow().message()).isEqualTo(message(Lane.FAST, 3));
    assertThat(queue.poll(Lane.FAST)).isEmpty();
    assertThat(sqs.deletes).isEmpty();
  }

  @Test
  void should_delete_on_acknowledge_from_the_right_queue() throws Exception {
    sqs.enqueue(STANDARD_URL, ProposalMessageCodec.toJson(message(Lane.STANDARD, 4)), "1");
    SqsLaneQueueAdapter queue = adapter();
    await(() -> queue.hasMessages(Lane.STANDARD));

    queue.acknowledge(queue.poll(Lane.STANDARD).orElseThrow());

    assertThat(sqs.deletes).hasSize(1);
    assertThat(sqs.deletes.get(0).queueUrl()).isEqualTo(STANDARD_URL);
    assertThat(sqs.deletes.get(0).receiptHandle()).startsWith("h-");
  }

  @Test
  void should_change_visibility_on_release_clamping_the_delay() throws Exception {
    sqs.enqueue(FAST_URL, ProposalMessageCodec.toJson(message(Lane.FAST, 5)), "1");
    SqsLaneQueueAdapter queue = adapter();
    await(() -> queue.hasMessages(Lane.FAST));
    QueuedMessage message = queue.poll(Lane.FAST).orElseThrow();

    queue.release(message, Duration.ofSeconds(20));
    queue.release(message, Duration.ofSeconds(-5));
    queue.release(message, Duration.ofDays(30));

    assertThat(sqs.visibilities)
        .extracting(ChangeMessageVisibilityRequest::visibilityTimeout)
        .containsExactly(20, 0, 43_200);
    assertThat(sqs.visibilities.get(0).queueUrl()).isEqualTo(FAST_URL);
  }

  @Test
  void should_not_fail_when_delete_or_visibility_calls_fail() throws Exception {
    sqs.enqueue(FAST_URL, ProposalMessageCodec.toJson(message(Lane.FAST, 6)), "1");
    SqsLaneQueueAdapter queue = adapter();
    await(() -> queue.hasMessages(Lane.FAST));
    QueuedMessage message = queue.poll(Lane.FAST).orElseThrow();
    sqs.failDelete = true;

    queue.acknowledge(message);
    queue.release(message, Duration.ofSeconds(1));

    assertThat(sqs.deletes).isEmpty();
  }

  @Test
  void should_read_the_approximate_depth_and_default_to_zero_on_failure() {
    SqsLaneQueueAdapter queue = adapter();

    assertThat(queue.approximateDepth(Lane.FAST)).isEqualTo(42);
    sqs.depthValue = "nao-numero";
    assertThat(queue.approximateDepth(Lane.FAST)).isZero();
    sqs.failDepth = true;
    assertThat(queue.approximateDepth(Lane.STANDARD)).isZero();
  }

  @Test
  void should_survive_receive_failures_and_recover() throws Exception {
    sqs.failReceive = true;
    SqsLaneQueueAdapter queue = adapter();
    Thread.sleep(100);
    sqs.enqueue(FAST_URL, ProposalMessageCodec.toJson(message(Lane.FAST, 7)), "1");

    sqs.failReceive = false;

    await(() -> queue.hasMessages(Lane.FAST));
  }

  @Test
  void should_reject_a_prefetch_smaller_than_one_batch() {
    assertThatThrownBy(() -> new SqsLaneQueueAdapter(sqs, FAST_URL, STANDARD_URL, 5, 1, 30))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
