package br.com.consignado.platform.ingestion.domain;

/**
 * O DurableInbox nao pode gravar agora; a proposta NAO foi aceita (HTTP 503, o parceiro repete).
 */
public class InboxUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InboxUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
