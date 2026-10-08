package br.com.consignado.platform.localenv;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.dispatcher.config.DispatcherConfig;
import br.com.consignado.platform.shared.contract.InboxAttributes;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.contract.ProposalMessage;
import br.com.consignado.platform.shared.contract.ProposalMessageCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * DynamoDB Stream real (DynamoDB Local) + SQS real (ElasticMQ) + o mesmo BatchDispatcher do Lambda:
 * itens inseridos no DurableInbox viram mensagens na lane certa, o item passa a DISPATCHED com o
 * escore e nada e republicado ao ler o Stream de novo.
 */
class DispatcherEndToEndIT {

  private static AttributeValue s(String value) {
    return AttributeValue.builder().s(value).build();
  }

  private static void insert(
      DynamoDbClient dynamo, String key, String tenant, boolean portability, String amount) {
    dynamo.putItem(
        PutItemRequest.builder()
            .tableName(InboxAttributes.TABLE_NAME)
            .item(
                Map.of(
                    InboxAttributes.IDEMPOTENCY_KEY, s(key),
                    InboxAttributes.TENANT_ID, s(tenant),
                    InboxAttributes.CONVENIO_TYPE, s("INSS"),
                    InboxAttributes.PORTABILITY, AttributeValue.builder().bool(portability).build(),
                    InboxAttributes.AMOUNT, AttributeValue.builder().n(amount).build(),
                    InboxAttributes.PAYLOAD, s("payload-cifrado"),
                    InboxAttributes.STATUS, s(InboxAttributes.STATUS_RECEIVED),
                    InboxAttributes.TRACING_ID, s("trace-" + key.substring(0, 4))))
            .build());
  }

  private static List<Message> drain(SqsClient sqs, String url) {
    List<Message> all = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      List<Message> batch =
          sqs.receiveMessage(
                  ReceiveMessageRequest.builder()
                      .queueUrl(url)
                      .maxNumberOfMessages(10)
                      .waitTimeSeconds(1)
                      .messageAttributeNames("All")
                      .build())
              .messages();
      all.addAll(batch);
    }
    return all;
  }

  @Test
  void should_route_stream_records_to_the_right_lane_and_mark_them_dispatched() throws Exception {
    try (LocalDynamoDb dynamo = LocalDynamoDb.start(0);
        DynamoDbClient dynamoClient = dynamo.client();
        LocalSqs sqs = LocalSqs.start(0);
        SqsClient sqsClient = sqs.client()) {
      dynamo.createInboxTable(dynamoClient, InboxAttributes.TABLE_NAME);
      LocalSqs.QueueUrls queues = sqs.createLaneQueues(sqsClient);
      DispatcherConfig config =
          DispatcherConfig.fromEnvironment(
              Map.of(
                  "DISPATCHER_FAST_QUEUE_URL", queues.fast(),
                  "DISPATCHER_STANDARD_QUEUE_URL", queues.standard(),
                  "DISPATCHER_VIP_TENANTS", "vip-bank"));
      try (LocalStreamDispatcher dispatcher =
          new LocalStreamDispatcher(
              dynamo,
              dynamoClient,
              InboxAttributes.TABLE_NAME,
              config.batchDispatcher(sqsClient, dynamoClient))) {

        String portabilityKey = "aaaa0000-0000-4000-8000-000000000001";
        String vipKey = "bbbb0000-0000-4000-8000-000000000002";
        String standardKey = "cccc0000-0000-4000-8000-000000000003";
        String poisonKey = "nao-e-uuid";
        insert(dynamoClient, portabilityKey, "banco-a", true, "1000.00");
        insert(dynamoClient, vipKey, "vip-bank", false, "1000.00");
        insert(dynamoClient, standardKey, "banco-a", false, "1000.00");
        insert(dynamoClient, poisonKey, "banco-a", true, "1000.00");

        int read = dispatcher.pollOnce();
        assertThat(read).isGreaterThanOrEqualTo(4);

        List<Message> fast = drain(sqsClient, queues.fast());
        List<Message> standard = drain(sqsClient, queues.standard());
        assertThat(fast)
            .extracting(m -> ProposalMessageCodec.fromJson(m.body()).key().toString())
            .containsExactlyInAnyOrder(portabilityKey, vipKey);
        assertThat(standard)
            .extracting(m -> ProposalMessageCodec.fromJson(m.body()).key().toString())
            .containsExactly(standardKey);
        ProposalMessage fastMessage = ProposalMessageCodec.fromJson(fast.get(0).body());
        assertThat(fastMessage.lane()).isEqualTo(Lane.FAST);
        assertThat(fast.get(0).messageAttributes().get("tracingId").stringValue())
            .startsWith("trace-");
        assertThat(fast.get(0).body()).doesNotContain("payload-cifrado").doesNotContain("1000");

        for (String key : List.of(portabilityKey, vipKey, standardKey)) {
          Map<String, AttributeValue> item =
              dynamoClient
                  .getItem(
                      GetItemRequest.builder()
                          .tableName(InboxAttributes.TABLE_NAME)
                          .key(Map.of(InboxAttributes.IDEMPOTENCY_KEY, s(key)))
                          .consistentRead(true)
                          .build())
                  .item();
          assertThat(item.get(InboxAttributes.STATUS).s()).isEqualTo("DISPATCHED");
          assertThat(item.get(InboxAttributes.PRIORITY_SCORE).n()).isNotBlank();
        }
        assertThat(
                dynamoClient
                    .getItem(
                        GetItemRequest.builder()
                            .tableName(InboxAttributes.TABLE_NAME)
                            .key(Map.of(InboxAttributes.IDEMPOTENCY_KEY, s(poisonKey)))
                            .consistentRead(true)
                            .build())
                    .item()
                    .get(InboxAttributes.STATUS)
                    .s())
            .as("item malformado nao e despachado")
            .isEqualTo("RECEIVED");

        // ler o Stream de novo nao republica nada
        dispatcher.pollOnce();
        assertThat(drain(sqsClient, queues.fast())).isEmpty();
        assertThat(drain(sqsClient, queues.standard())).isEmpty();
      }
    }
  }
}
