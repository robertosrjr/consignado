package br.com.consignado.platform.worker.application.port.in;

/**
 * Resultado do processamento de uma mensagem: {@code COMPLETED} (conclui e remove da fila), {@code
 * RETRY} (falha transitoria: volta a fila com atraso) ou {@code UNPROCESSABLE} (nao ha como
 * concluir, por exemplo item ausente no inbox: volta a fila e o redrive do SQS a envia a DLQ).
 */
public enum ProcessingOutcome {
  COMPLETED,
  RETRY,
  UNPROCESSABLE
}
