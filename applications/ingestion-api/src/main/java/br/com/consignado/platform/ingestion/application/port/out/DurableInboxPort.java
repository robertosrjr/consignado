package br.com.consignado.platform.ingestion.application.port.out;

/** Porta de saida: escrita condicional atomica no DurableInbox. */
public interface DurableInboxPort {

  /**
   * Grava o item somente se a chave de idempotencia ainda nao existe.
   *
   * @throws br.com.consignado.platform.ingestion.domain.InboxUnavailableException se o
   *     armazenamento nao puder confirmar a escrita
   */
  InboxWriteResult putIfAbsent(InboxItem item);
}
