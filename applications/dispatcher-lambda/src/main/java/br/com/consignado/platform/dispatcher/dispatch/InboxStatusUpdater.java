package br.com.consignado.platform.dispatcher.dispatch;

import br.com.consignado.platform.shared.vo.IdempotencyKey;

/** Porta: marca o item como despachado (somente se ainda estiver RECEIVED). */
public interface InboxStatusUpdater {

  default void markDispatched(
      IdempotencyKey key, int priorityScore, br.com.consignado.platform.shared.contract.Lane lane) {
    markDispatched(key, priorityScore);
  }

  /** Atualizacao condicional e idempotente; se ja estava despachado, nao faz nada. */
  void markDispatched(IdempotencyKey key, int priorityScore);
}
