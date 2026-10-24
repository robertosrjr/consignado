package br.com.consignado.platform.localenv.incident;

/**
 * Destino final de um incidente. Implementacoes devem ser idempotentes o bastante para tolerar a
 * reentrega da mensagem quando {@link #publish} falha antes do delete na fila.
 */
public interface IncidentNotifier {

  /**
   * @throws RuntimeException se o destino nao aceitou; a mensagem volta para a fila
   */
  void publish(Incident incident);
}
