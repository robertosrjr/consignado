package br.com.consignado.platform.ingestion.domain;

/** A technical key cannot be rebound to another intent. Contains no request data. */
public final class IdempotencyMismatchException extends RuntimeException {
  public IdempotencyMismatchException() {
    super("Idempotency-Key reutilizada com conteudo diferente");
  }
}
