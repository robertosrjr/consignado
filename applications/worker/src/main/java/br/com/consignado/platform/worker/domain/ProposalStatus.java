package br.com.consignado.platform.worker.domain;

/**
 * Estados da proposta no Worker. {@code PENDING} e criada antes de chamar o legado; {@code
 * SENT_TO_LEGACY} indica que a chamada foi iniciada (pode ter sido repetida com a mesma chave de
 * idempotencia); {@code CONFIRMED} e {@code FAILED} sao terminais.
 */
public enum ProposalStatus {
  PENDING,
  SENT_TO_LEGACY,
  CONFIRMED,
  FAILED;

  public boolean isFinal() {
    return this == CONFIRMED || this == FAILED;
  }
}
