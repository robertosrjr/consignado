package br.com.consignado.platform.worker.application.port.out;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.contract.ProposalMessage;

/** Mensagem recebida de uma lane, com o numero de recebimentos (base do backoff). */
public interface QueuedMessage {

  Lane lane();

  ProposalMessage message();

  /** Quantas vezes a fila entregou esta mensagem (1 na primeira entrega). */
  int receiveCount();
}
