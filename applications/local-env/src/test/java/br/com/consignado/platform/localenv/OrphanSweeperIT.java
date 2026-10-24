package br.com.consignado.platform.localenv;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.dispatcher.config.DispatcherConfig;
import br.com.consignado.platform.shared.contract.InboxAttributes;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

class OrphanSweeperIT {
  private static AttributeValue s(String value) {
    return AttributeValue.builder().s(value).build();
  }

  @Test
  void should_reconcile_an_inbox_orphan_without_the_stream_dispatcher() throws Exception {
    try (LocalDynamoDb server = LocalDynamoDb.start(0);
        var dynamo = server.client();
        LocalSqs queues = LocalSqs.start(0);
        var sqs = queues.client()) {
      server.createInboxTable(dynamo, InboxAttributes.TABLE_NAME);
      var urls = queues.createLaneQueues(sqs);
      var config =
          DispatcherConfig.fromEnvironment(
              Map.of(
                  "DISPATCHER_FAST_QUEUE_URL", urls.fast(),
                  "DISPATCHER_STANDARD_QUEUE_URL", urls.standard()));
      Map<String, AttributeValue> item = new HashMap<>();
      item.put(InboxAttributes.IDEMPOTENCY_KEY, s(UUID.randomUUID().toString()));
      item.put(InboxAttributes.TENANT_ID, s("banco-a"));
      item.put(InboxAttributes.CONVENIO_TYPE, s("INSS"));
      item.put(InboxAttributes.PORTABILITY, AttributeValue.builder().bool(true).build());
      item.put(InboxAttributes.AMOUNT, AttributeValue.builder().n("1000.00").build());
      item.put(InboxAttributes.STATUS, s(InboxAttributes.STATUS_RECEIVED));
      item.put(InboxAttributes.DISPATCH_PENDING, s(InboxAttributes.DISPATCH_PENDING_VALUE));
      item.put(InboxAttributes.CREATED_AT, AttributeValue.builder().n("1000").build());
      dynamo.putItem(
          PutItemRequest.builder().tableName(InboxAttributes.TABLE_NAME).item(item).build());
      var sweeper = config.orphanSweeper(sqs, dynamo, Clock.systemUTC(), Duration.ZERO, 10);
      assertThat(sweeper.sweep().redispatched()).isEqualTo(1);
      assertThat(sweeper.sweep().found()).isZero();
      assertThat(
              sqs.receiveMessage(
                      ReceiveMessageRequest.builder()
                          .queueUrl(urls.fast())
                          .waitTimeSeconds(1)
                          .build())
                  .messages())
          .hasSize(1);
    }
  }
}
