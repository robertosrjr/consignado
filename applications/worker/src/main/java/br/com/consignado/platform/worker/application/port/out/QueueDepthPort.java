package br.com.consignado.platform.worker.application.port.out;

import br.com.consignado.platform.shared.contract.Lane;

/** Porta de saida: profundidade aproximada das filas (base de net_backlog_growth e drain time). */
public interface QueueDepthPort {

  long approximateDepth(Lane lane);

  /** Profundidade da DLQ; negativo quando nao ha DLQ conhecida (sinal ausente). */
  default long dlqDepth() {
    return -1;
  }
}
