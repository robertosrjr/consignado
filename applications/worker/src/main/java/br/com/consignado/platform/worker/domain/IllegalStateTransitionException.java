package br.com.consignado.platform.worker.domain;

/** Transicao de estado invalida da proposta (por exemplo, sair de um estado terminal). */
public class IllegalStateTransitionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public IllegalStateTransitionException(ProposalStatus from, String action) {
    super("Transicao invalida: " + action + " a partir de " + from);
  }
}
