package br.com.consignado.platform.ingestion.infrastructure.adapter.in.web;

/** O tenant informado no header nao corresponde ao tenant do token (HTTP 403). */
public class TenantForbiddenException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public TenantForbiddenException() {
    super("Tenant nao autorizado para este token");
  }
}
