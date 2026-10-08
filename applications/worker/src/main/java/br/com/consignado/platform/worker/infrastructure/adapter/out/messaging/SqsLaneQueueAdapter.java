package br.com.consignado.platform.worker.infrastructure.adapter.out.messaging;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.contract.ProposalMessage;
import br.com.consignado.platform.shared.contract.ProposalMessageCodec;
import br.com.consignado.platform.worker.application.port.out.LaneQueuePort;
import br.com.consignado.platform.worker.application.port.out.QueueDepthPort;
import br.com.consignado.platform.worker.application.port.out.QueuedMessage;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * Filas das lanes no SQS com pre-busca local: uma thread (virtual) por lane faz long polling e
 * alimenta um buffer limitado; o laco de despacho retira do buffer conforme o orcamento. O buffer e
 * pequeno para que as mensagens aguardem muito menos que o visibility timeout. Mensagens ilegiveis
 * ("venenosas") nao sao removidas: o redrive do SQS as envia a DLQ apos as tentativas configuradas.
 */
public final class SqsLaneQueueAdapter implements LaneQueuePort, QueueDepthPort, AutoCloseable {

  private static final Logger LOG = System.getLogger(SqsLaneQueueAdapter.class.getName());
  private static final int BATCH = 10;

  private record SqsMessage(
      Lane lane, ProposalMessage message, String receiptHandle, int receiveCount)
      implements QueuedMessage {}

  private final SqsClient sqs;
  private final String fastQueueUrl;
  private final String standardQueueUrl;
  private final int waitSeconds;
  private final int visibilityTimeoutSeconds;
  private final BlockingQueue<SqsMessage> fastBuffer;
  private final BlockingQueue<SqsMessage> standardBuffer;
  private final int prefetch;
  private volatile boolean running;
  private Thread fastThread;
  private Thread standardThread;

  public SqsLaneQueueAdapter(
      SqsClient sqs,
      String fastQueueUrl,
      String standardQueueUrl,
      int prefetch,
      int waitSeconds,
      int visibilityTimeoutSeconds) {
    if (prefetch < BATCH) {
      throw new IllegalArgumentException("Pre-busca deve ser de pelo menos " + BATCH);
    }
    this.sqs = sqs;
    this.fastQueueUrl = fastQueueUrl;
    this.standardQueueUrl = standardQueueUrl;
    this.prefetch = prefetch;
    this.waitSeconds = waitSeconds;
    this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
    this.fastBuffer = new LinkedBlockingQueue<>(prefetch);
    this.standardBuffer = new LinkedBlockingQueue<>(prefetch);
  }

  /** Inicia as threads de long polling (uma por lane). */
  public synchronized void start() {
    if (running) {
      return;
    }
    running = true;
    fastThread = Thread.ofVirtual().name("sqs-poll-fast").start(() -> receiveLoop(Lane.FAST));
    standardThread =
        Thread.ofVirtual().name("sqs-poll-standard").start(() -> receiveLoop(Lane.STANDARD));
  }

  @Override
  public synchronized void close() {
    running = false;
    if (fastThread != null) {
      fastThread.interrupt();
    }
    if (standardThread != null) {
      standardThread.interrupt();
    }
  }

  @Override
  public boolean hasMessages(Lane lane) {
    return !buffer(lane).isEmpty();
  }

  @Override
  public Optional<QueuedMessage> poll(Lane lane) {
    return Optional.ofNullable(buffer(lane).poll());
  }

  @Override
  public void acknowledge(QueuedMessage message) {
    SqsMessage sqsMessage = (SqsMessage) message;
    try {
      sqs.deleteMessage(
          DeleteMessageRequest.builder()
              .queueUrl(url(sqsMessage.lane()))
              .receiptHandle(sqsMessage.receiptHandle())
              .build());
    } catch (SdkException e) {
      // a mensagem reaparece e sera reprocessada com idempotencia
      LOG.log(Level.WARNING, "event=sqs_delete_failed lane={0}", sqsMessage.lane());
    }
  }

  @Override
  public void release(QueuedMessage message, Duration delay) {
    SqsMessage sqsMessage = (SqsMessage) message;
    try {
      sqs.changeMessageVisibility(
          ChangeMessageVisibilityRequest.builder()
              .queueUrl(url(sqsMessage.lane()))
              .receiptHandle(sqsMessage.receiptHandle())
              .visibilityTimeout((int) Math.min(Math.max(delay.toSeconds(), 0), 43_200))
              .build());
    } catch (SdkException e) {
      LOG.log(Level.WARNING, "event=sqs_visibility_failed lane={0}", sqsMessage.lane());
    }
  }

  @Override
  public long approximateDepth(Lane lane) {
    try {
      String value =
          sqs.getQueueAttributes(
                  GetQueueAttributesRequest.builder()
                      .queueUrl(url(lane))
                      .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                      .build())
              .attributesAsStrings()
              .get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES.toString());
      return value == null ? 0 : Long.parseLong(value);
    } catch (SdkException | NumberFormatException e) {
      return 0;
    }
  }

  private void receiveLoop(Lane lane) {
    BlockingQueue<SqsMessage> buffer = buffer(lane);
    while (running) {
      try {
        if (buffer.remainingCapacity() < BATCH) {
          Thread.sleep(20);
          continue;
        }
        List<Message> received =
            sqs.receiveMessage(
                    ReceiveMessageRequest.builder()
                        .queueUrl(url(lane))
                        .maxNumberOfMessages(BATCH)
                        .waitTimeSeconds(waitSeconds)
                        .visibilityTimeout(visibilityTimeoutSeconds)
                        .messageSystemAttributeNames(
                            MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                        .build())
                .messages();
        for (Message message : received) {
          accept(lane, buffer, message);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (SdkException e) {
        LOG.log(Level.WARNING, "event=sqs_receive_failed lane={0}", lane);
        pause(1_000);
      }
    }
  }

  private void accept(Lane lane, BlockingQueue<SqsMessage> buffer, Message message) {
    try {
      ProposalMessage parsed = ProposalMessageCodec.fromJson(message.body());
      String count = message.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
      int receiveCount = count == null ? 1 : Integer.parseInt(count);
      if (!buffer.offer(new SqsMessage(lane, parsed, message.receiptHandle(), receiveCount))) {
        // buffer cheio (nao deveria ocorrer): deixa a mensagem reaparecer
        LOG.log(Level.WARNING, "event=prefetch_buffer_full lane={0}", lane);
      }
    } catch (RuntimeException e) {
      LOG.log(Level.ERROR, "event=poison_message lane={0}", lane);
    }
  }

  private BlockingQueue<SqsMessage> buffer(Lane lane) {
    return lane == Lane.FAST ? fastBuffer : standardBuffer;
  }

  private String url(Lane lane) {
    return lane == Lane.FAST ? fastQueueUrl : standardQueueUrl;
  }

  private static void pause(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Tamanho configurado da pre-busca (diagnostico). */
  int prefetch() {
    return prefetch;
  }
}
