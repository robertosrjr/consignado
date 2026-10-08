package br.com.consignado.platform.dispatcher.dispatch;

/**
 * Visao neutra de um registro do DynamoDB Stream (independente do SDK/Lambda de origem). Os campos
 * vem em texto, como no item; a validacao ocorre em {@link DispatchService}.
 */
public record StreamRecordView(
    String eventName,
    String sequenceNumber,
    String idempotencyKey,
    String tenantId,
    String convenioType,
    Boolean portability,
    String amount,
    String tracingId,
    String status) {

  public static final String INSERT = "INSERT";

  public boolean isInsert() {
    return INSERT.equals(eventName);
  }

  /** Nunca imprime dados do item alem de identificadores tecnicos. */
  @Override
  public String toString() {
    return "StreamRecordView[event=" + eventName + ", sequence=" + sequenceNumber + "]";
  }
}
