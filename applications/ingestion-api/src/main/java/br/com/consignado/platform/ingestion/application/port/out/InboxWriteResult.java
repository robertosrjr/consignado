package br.com.consignado.platform.ingestion.application.port.out;

/**
 * Resultado da escrita condicional. Em caso de duplicidade traz o tracingId e o instante originais
 * (quando o armazenamento os devolve).
 */
public record InboxWriteResult(
    boolean created,
    String existingTracingId,
    Long existingCreatedAt,
    String proposalId,
    boolean semanticDuplicate) {

  public InboxWriteResult(boolean created, String tracingId, Long createdAt) {
    this(created, tracingId, createdAt, null, false);
  }

  public static InboxWriteResult createdNew() {
    return new InboxWriteResult(true, null, null);
  }

  public static InboxWriteResult alreadyExists(String tracingId, Long createdAtEpochMillis) {
    return new InboxWriteResult(false, tracingId, createdAtEpochMillis);
  }
}
