package br.com.consignado.platform.worker.application.port.out;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.worker.application.port.in.ProcessingOutcome;

/** Observador do laco de despacho (metricas), sem acoplar o caso de uso a um framework. */
public interface DispatchObserver {

  DispatchObserver NONE =
      new DispatchObserver() {
        @Override
        public void dispatched(Lane lane) {}

        @Override
        public void processed(Lane lane, ProcessingOutcome outcome) {}

        @Override
        public void failed(Lane lane) {}
      };

  /** Uma mensagem foi retirada da lane e entregue ao processamento. */
  void dispatched(Lane lane);

  /** O processamento terminou com o resultado informado. */
  void processed(Lane lane, ProcessingOutcome outcome);

  /** O processamento lancou uma excecao inesperada. */
  void failed(Lane lane);
}
