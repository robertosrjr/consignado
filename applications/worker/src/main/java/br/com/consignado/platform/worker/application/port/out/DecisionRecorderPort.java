package br.com.consignado.platform.worker.application.port.out;

import br.com.consignado.platform.shared.vo.IdempotencyKey;
import java.time.Instant;

/**
 * Porta de saida: registra a decisao terminal no DurableInbox para a medicao do SLO J2. Falhas nao
 * interrompem o processamento; a proposta passa a contar como ruim no prazo (lado conservador).
 */
public interface DecisionRecorderPort {

  DecisionRecorderPort NONE = (key, confirmed, at) -> {};

  /**
   * @param confirmed {@code true} se averbada; {@code false} para rejeicao comercial do legado
   */
  void recordDecision(IdempotencyKey key, boolean confirmed, Instant at);
}
