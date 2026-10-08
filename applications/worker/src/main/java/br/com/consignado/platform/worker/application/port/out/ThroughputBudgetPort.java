package br.com.consignado.platform.worker.application.port.out;

import br.com.consignado.platform.shared.contract.Lane;

/**
 * Porta de saida: orcamento de vazao ate o legado. A primeira implementacao divide o teto global
 * entre as instancias (180 / N): protecao local ao processo, documentada.
 */
public interface ThroughputBudgetPort {

  BudgetSnapshot snapshot();

  /** Consome uma permissao para a lane (global e, se disponivel, o piso da lane). */
  void consume(Lane lane);
}
