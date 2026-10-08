package br.com.consignado.platform.dispatcher.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.shared.contract.InboxAttributes;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.contract.ProposalMessage;
import br.com.consignado.platform.shared.contract.ProposalMessageCodec;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

class AwsAdaptersTest {

  private static final ProposalMessage MESSAGE =
      ProposalMessage.of(
          IdempotencyKey.parse("e3b0c442-98fc-11ee-b9d1-0242ac120002"),
          new TenantId("banco-a"),
          Lane.FAST,
          60);

  private static final class FakeSqs implements SqsClient {
    final List<SendMessageRequest> sent = new ArrayList<>();
    RuntimeException failure;

    @Override
    public SendMessageResponse sendMessage(SendMessageRequest request) {
      if (failure != null) {
        throw failure;
      }
      sent.add(request);
      return SendMessageResponse.builder().messageId("m-1").build();
    }

    @Override
    public String serviceName() {
      return "sqs";
    }

    @Override
    public void close() {}
  }

  private static final class FakeDynamo implements DynamoDbClient {
    UpdateItemRequest last;
    RuntimeException failure;

    @Override
    public UpdateItemResponse updateItem(UpdateItemRequest request) {
      last = request;
      if (failure != null) {
        throw failure;
      }
      return UpdateItemResponse.builder().build();
    }

    @Override
    public String serviceName() {
      return "dynamodb";
    }

    @Override
    public void close() {}
  }

  @Test
  void should_send_to_the_queue_of_the_lane_with_the_json_body_and_tracing_attribute() {
    FakeSqs sqs = new FakeSqs();
    SqsQueuePublisher publisher = new SqsQueuePublisher(sqs, "http://q/fast", "http://q/standard");

    publisher.publish(Lane.FAST, MESSAGE, "trace-1");
    publisher.publish(Lane.STANDARD, MESSAGE, null);

    assertThat(sqs.sent.get(0).queueUrl()).isEqualTo("http://q/fast");
    assertThat(sqs.sent.get(0).messageBody()).isEqualTo(ProposalMessageCodec.toJson(MESSAGE));
    assertThat(sqs.sent.get(0).messageAttributes().get("tracingId").stringValue())
        .isEqualTo("trace-1");
    assertThat(sqs.sent.get(1).queueUrl()).isEqualTo("http://q/standard");
    assertThat(sqs.sent.get(1).messageAttributes()).isEmpty();
  }

  @Test
  void should_not_leak_sdk_details_when_publishing_fails() {
    FakeSqs sqs = new FakeSqs();
    sqs.failure = SdkClientException.create("connect timeout to 10.1.2.3");
    SqsQueuePublisher publisher = new SqsQueuePublisher(sqs, "f", "s");

    assertThatThrownBy(() -> publisher.publish(Lane.FAST, MESSAGE, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining("10.1.2.3");
  }

  @Test
  void should_update_conditionally_from_received_to_dispatched() {
    FakeDynamo dynamo = new FakeDynamo();
    DynamoInboxStatusUpdater updater = new DynamoInboxStatusUpdater(dynamo, "DurableInbox");

    updater.markDispatched(MESSAGE.key(), 60);

    UpdateItemRequest request = dynamo.last;
    assertThat(request.tableName()).isEqualTo("DurableInbox");
    assertThat(request.key().get(InboxAttributes.IDEMPOTENCY_KEY).s())
        .isEqualTo("e3b0c442-98fc-11ee-b9d1-0242ac120002");
    assertThat(request.conditionExpression()).isEqualTo("#status = :received");
    assertThat(request.expressionAttributeValues().get(":dispatched").s()).isEqualTo("DISPATCHED");
    assertThat(request.expressionAttributeValues().get(":received").s()).isEqualTo("RECEIVED");
    assertThat(request.expressionAttributeValues().get(":score").n()).isEqualTo("60");
  }

  @Test
  void should_ignore_a_conditional_failure_because_the_item_was_already_dispatched() {
    FakeDynamo dynamo = new FakeDynamo();
    dynamo.failure = ConditionalCheckFailedException.builder().message("x").build();
    DynamoInboxStatusUpdater updater = new DynamoInboxStatusUpdater(dynamo, "DurableInbox");

    assertThatCode(() -> updater.markDispatched(MESSAGE.key(), 60)).doesNotThrowAnyException();
  }

  @Test
  void should_translate_other_failures_without_leaking_details() {
    FakeDynamo dynamo = new FakeDynamo();
    dynamo.failure = SdkClientException.create("throttled at 10.9.9.9");
    DynamoInboxStatusUpdater updater = new DynamoInboxStatusUpdater(dynamo, "DurableInbox");

    assertThatThrownBy(() -> updater.markDispatched(MESSAGE.key(), 60))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining("10.9.9.9");
  }
}
