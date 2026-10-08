package br.com.consignado.platform.dispatcher.dispatch;

import java.util.List;

/** Resultado de um lote: contagens por lane e os numeros de sequencia que falharam. */
public record BatchResult(
    int dispatchedFast, int dispatchedStandard, int skipped, List<String> failedSequenceNumbers) {

  public int failed() {
    return failedSequenceNumbers.size();
  }
}
