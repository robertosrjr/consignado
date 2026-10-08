package br.com.consignado.platform.shared.contract;

/** Falha ao (de)serializar um contrato; a mensagem nunca inclui o conteudo recebido. */
public class MessageContractException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public MessageContractException(String message, Throwable cause) {
    super(message, cause);
  }
}
