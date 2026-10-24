package br.com.consignado.platform.worker.infrastructure.adapter.out.persistence;

import br.com.consignado.platform.worker.domain.ProposalStatus;

/** Contagem de propostas por estado, base da metrica {@code worker.proposals}. */
public interface ProposalStatusCounts {

  long count(ProposalStatus status);
}
