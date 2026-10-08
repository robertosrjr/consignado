package br.com.consignado.platform.ingestion.infrastructure.adapter.in.web;

/** Requisicao invalida (HTTP 400). A mensagem cita apenas o campo, nunca o valor recebido. */
public class InvalidRequestException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidRequestException(String field) {
    super("Campo invalido: " + field);
  }
}
