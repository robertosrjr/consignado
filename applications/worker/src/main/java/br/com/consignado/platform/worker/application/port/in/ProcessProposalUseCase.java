package br.com.consignado.platform.worker.application.port.in;

import br.com.consignado.platform.shared.contract.ProposalMessage;

/** Porta de entrada: leva uma proposta ate o estado final sem averbacao duplicada. */
public interface ProcessProposalUseCase {

  ProcessingOutcome process(ProposalMessage message);
}
