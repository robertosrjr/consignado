package br.com.consignado.platform.worker.domain;

/** Resultado de uma chamada ao legado. */
public sealed interface LegacyResult {

  /** Averbacao registrada (ou repetida, se a chave ja existia). */
  record Confirmed(String averbacaoId) implements LegacyResult {}

  /** Recusa de negocio: nao adianta repetir. */
  record Rejected(String reason) implements LegacyResult {}

  /** Falha transitoria (capacidade, 5xx, timeout, circuito aberto): repetir mais tarde. */
  record TransientFailure() implements LegacyResult {}
}
