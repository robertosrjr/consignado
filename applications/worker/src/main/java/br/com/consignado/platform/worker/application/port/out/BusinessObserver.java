package br.com.consignado.platform.worker.application.port.out;

/** Observador de fatos de negocio do processamento (metricas de VGV, conversao e SLA). */
public interface BusinessObserver {

  BusinessObserver NONE = settlement -> {};

  /** A proposta foi averbada (confirmada) ou rejeitada pelo legado, exatamente uma vez. */
  void settled(ProposalSettlement settlement);
}
