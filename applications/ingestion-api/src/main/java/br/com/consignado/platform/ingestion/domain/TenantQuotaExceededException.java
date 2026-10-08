package br.com.consignado.platform.ingestion.domain;

import br.com.consignado.platform.shared.vo.TenantId;

/** A cota do parceiro foi excedida (HTTP 429), sem afetar os demais parceiros. */
public class TenantQuotaExceededException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient TenantId tenant;

  public TenantQuotaExceededException(TenantId tenant) {
    super("Cota do parceiro excedida");
    this.tenant = tenant;
  }

  public TenantId tenant() {
    return tenant;
  }
}
