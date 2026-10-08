package br.com.consignado.platform.shared.crypto;

/** Falha ao proteger ou recuperar um payload; a mensagem nunca inclui conteudo ou chave. */
public class PayloadProtectionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public PayloadProtectionException(String message, Throwable cause) {
    super(message, cause);
  }
}
