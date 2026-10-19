package br.com.consignado.platform.dispatcher.aws;

import br.com.consignado.platform.dispatcher.dispatch.QueuePublisher;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.contract.ProposalMessage;
import br.com.consignado.platform.shared.contract.ProposalMessageCodec;
import java.util.Map;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/** Publica {@link ProposalMessage} (sem PII) na fila da lane; o tracingId segue como atributo. */
public final class SqsQueuePublisher implements QueuePublisher {

  static final String TRACING_ATTRIBUTE = "tracingId";

  private final SqsClient sqs;
  private final String fastQueueUrl;
  private final String standardQueueUrl;

  public SqsQueuePublisher(SqsClient sqs, String fastQueueUrl, String standardQueueUrl) {
    this.sqs = sqs;
    this.fastQueueUrl = fastQueueUrl;
    this.standardQueueUrl = standardQueueUrl;
  }

  @Override
  public void publish(Lane lane, ProposalMessage message, String tracingId) {
    publish(lane, message, tracingId, Map.of());
  }

  @Override
  public void publish(
      Lane lane, ProposalMessage message, String tracingId, Map<String, String> traceContext) {
    try (var operation =
        br.com.consignado.platform.telemetry.Telemetry.start(
            "send proposal",
            io.opentelemetry.api.trace.SpanKind.PRODUCER,
            br.com.consignado.platform.telemetry.TraceContext.extract(traceContext))) {
      operation.span().setAttribute("messaging.system", "aws_sqs");
      operation.span().setAttribute("messaging.operation.type", "send");
      operation.span().setAttribute("messaging.destination.name", lane.name());
      try {
        send(lane, message, tracingId);
      } catch (RuntimeException e) {
        operation.failed(e);
        throw e;
      }
    }
  }

  private void send(Lane lane, ProposalMessage message, String tracingId) {
    SendMessageRequest.Builder request =
        SendMessageRequest.builder()
            .queueUrl(lane == Lane.FAST ? fastQueueUrl : standardQueueUrl)
            .messageBody(ProposalMessageCodec.toJson(message));
    Map<String, MessageAttributeValue> attributes = new java.util.HashMap<>();
    if (tracingId != null && !tracingId.isBlank()) {
      attributes.put(
          TRACING_ATTRIBUTE,
          MessageAttributeValue.builder().dataType("String").stringValue(tracingId).build());
    }
    br.com.consignado.platform.telemetry.TraceContext.capture()
        .forEach(
            (name, value) ->
                attributes.put(
                    name,
                    MessageAttributeValue.builder().dataType("String").stringValue(value).build()));
    request.messageAttributes(attributes);
    try {
      sqs.sendMessage(request.build());
    } catch (SdkException e) {
      throw new IllegalStateException("Falha ao publicar na fila " + lane, e);
    }
  }
}
