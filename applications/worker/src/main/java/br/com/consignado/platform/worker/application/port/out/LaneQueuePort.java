package br.com.consignado.platform.worker.application.port.out;

import br.com.consignado.platform.shared.contract.Lane;
import java.time.Duration;
import java.util.Optional;

/** Porta de saida: filas das lanes (SQS ou memoria), com pre-busca local. */
public interface LaneQueuePort {

  boolean hasMessages(Lane lane);

  Optional<QueuedMessage> poll(Lane lane);

  /** Conclui a mensagem (remove da fila). */
  void acknowledge(QueuedMessage message);

  /** Devolve a mensagem a fila para nova tentativa apos o atraso (o redrive trata o limite). */
  void release(QueuedMessage message, Duration delay);
}
