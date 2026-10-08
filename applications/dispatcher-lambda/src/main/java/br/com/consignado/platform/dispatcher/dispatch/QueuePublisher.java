package br.com.consignado.platform.dispatcher.dispatch;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.contract.ProposalMessage;

/** Porta: publica a mensagem de roteamento na fila da lane. */
public interface QueuePublisher {

  /**
   * @param tracingId identificador de rastreio original (pode ser nulo)
   */
  void publish(Lane lane, ProposalMessage message, String tracingId);
}
