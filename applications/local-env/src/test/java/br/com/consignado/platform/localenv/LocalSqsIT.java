package br.com.consignado.platform.localenv;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/** ElasticMQ embarcado: filas das lanes e redrive para a DLQ apos 3 recebimentos (como na AWS). */
class LocalSqsIT {

  private static List<Message> receive(SqsClient sqs, String url, int visibilitySeconds) {
    return sqs.receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(url)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(1)
                .visibilityTimeout(visibilitySeconds)
                .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                .build())
        .messages();
  }

  @Test
  void should_create_the_three_queues_and_deliver_with_the_receive_count() throws Exception {
    try (LocalSqs local = LocalSqs.start(0);
        SqsClient sqs = local.client()) {
      LocalSqs.QueueUrls queues = local.createLaneQueues(sqs);
      sqs.sendMessage(
          SendMessageRequest.builder().queueUrl(queues.fast()).messageBody("m-1").build());

      List<Message> first = receive(sqs, queues.fast(), 1);

      assertThat(first).hasSize(1);
      assertThat(first.get(0).body()).isEqualTo("m-1");
      assertThat(
              first.get(0).attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT))
          .isEqualTo("1");
      assertThat(receive(sqs, queues.standard(), 1)).isEmpty();
    }
  }

  @Test
  void should_move_a_message_to_the_dlq_after_three_receives_without_delete() throws Exception {
    try (LocalSqs local = LocalSqs.start(0);
        SqsClient sqs = local.client()) {
      LocalSqs.QueueUrls queues = local.createLaneQueues(sqs);
      sqs.sendMessage(
          SendMessageRequest.builder().queueUrl(queues.standard()).messageBody("veneno").build());

      for (int attempt = 1; attempt <= 3; attempt++) {
        List<Message> received = receive(sqs, queues.standard(), 1);
        assertThat(received).as("entrega " + attempt).hasSize(1);
        Thread.sleep(1_200);
      }

      assertThat(receive(sqs, queues.standard(), 1)).as("fila principal vazia").isEmpty();
      List<Message> dead = receive(sqs, queues.dlq(), 5);
      assertThat(dead).hasSize(1);
      assertThat(dead.get(0).body()).isEqualTo("veneno");
    }
  }
}
