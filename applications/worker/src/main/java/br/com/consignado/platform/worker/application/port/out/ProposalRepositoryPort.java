package br.com.consignado.platform.worker.application.port.out;

import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.worker.domain.Proposal;
import br.com.consignado.platform.worker.domain.ProposalEvent;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saida: persistencia do estado da proposta e do historico de eventos. Primeira fase: em
 * memoria; a troca por banco relacional (DDL de referencia do SDD) nao muda os casos de uso.
 */
public interface ProposalRepositoryPort {

  /**
   * Cria a proposta em PENDING de forma atomica e idempotente (a chave e unica); quando cria,
   * registra o evento {@code PROPOSAL_CREATED} na mesma operacao.
   */
  CreationResult createPendingIfAbsent(ProposalSeed seed);

  /**
   * Persiste o novo estado e seus eventos de forma atomica. Nunca regride um estado terminal: se a
   * proposta ja estiver CONFIRMED ou FAILED, a gravacao e ignorada.
   */
  void save(Proposal proposal, List<ProposalEvent> events);

  Optional<Proposal> find(IdempotencyKey key);
}
