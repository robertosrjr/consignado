package br.com.consignado.platform.localenv.incident;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * Consumidor da fila de alertas (a mesma forma da SQS assinada no SNS em producao). Apaga a
 * mensagem so depois de publicar; falha do destino deixa a mensagem voltar pela visibilidade.
 * Mensagens invalidas sao apagadas e registradas, para nao bloquear a fila.
 */
public final class IncidentQueueConsumer implements AutoCloseable {

  private static final Logger LOG = System.getLogger(IncidentQueueConsumer.class.getName());

  private final SqsClient sqs;
  private final String queueUrl;
  private final IncidentProcessor processor;
  private volatile boolean running;
  private Thread worker;

  public IncidentQueueConsumer(SqsClient sqs, String queueUrl, IncidentProcessor processor) {
    this.sqs = sqs;
    this.queueUrl = queueUrl;
    this.processor = processor;
  }

  public void start() {
    running = true;
    worker = Thread.ofVirtual().name("incident-consumer").start(this::loop);
  }

  /** Um ciclo de leitura (publico para testes). */
  public int pollOnce(int waitSeconds) {
    int handled = 0;
    for (Message message :
        sqs.receiveMessage(
                ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(waitSeconds)
                    .build())
            .messages()) {
      try {
        processor.process(message.body());
      } catch (IllegalArgumentException poison) {
        LOG.log(Level.WARNING, "event=incident_message_invalid action=deleted");
      } catch (RuntimeException destinationFailure) {
        LOG.log(
            Level.WARNING,
            "event=incident_publish_failed error={0}",
            destinationFailure.getClass().getName());
        continue; // sem delete: reentrega apos a visibilidade
      }
      sqs.deleteMessage(
          DeleteMessageRequest.builder()
              .queueUrl(queueUrl)
              .receiptHandle(message.receiptHandle())
              .build());
      handled++;
    }
    return handled;
  }

  private void loop() {
    while (running) {
      try {
        pollOnce(5);
      } catch (SdkException e) {
        if (running) {
          LOG.log(
              Level.WARNING, "event=incident_queue_unavailable error={0}", e.getClass().getName());
        }
      }
    }
  }

  @Override
  public void close() {
    running = false;
    if (worker != null) {
      try {
        worker.join(10_000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
