package br.com.consignado.platform.worker.application.port.out;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import java.util.List;

/** Porta de saida: propostas roteadas ainda nao avaliadas contra o prazo do SLO J2. */
public interface DeadlineIndexPort {

  /** Proposta aberta no indice; {@code decidedAtEpochMillis} nulo se ainda sem decisao. */
  record OpenProposal(IdempotencyKey key, long createdAtEpochMillis, Long decidedAtEpochMillis) {}

  /** Ate {@code limit} propostas da lane aceitas em ou antes de {@code createdAtOrBefore}. */
  List<OpenProposal> acceptedUpTo(Lane lane, long createdAtOrBefore, int limit);

  /**
   * Fecha a proposta no indice gravando o resultado, de forma condicional.
   *
   * @return {@code true} se esta chamada fechou; {@code false} se outra replica ja havia fechado
   */
  boolean close(IdempotencyKey key, String outcome);
}
