package br.com.consignado.platform.ingestion.application.port.out;

import br.com.consignado.platform.shared.vo.TenantId;

/** Porta de saida: cota por parceiro. A protecao e local ao processo (documentado). */
public interface TenantQuotaPort {

  /** Consome uma permissao da cota do parceiro; {@code false} se a cota estourou. */
  boolean tryAcquire(TenantId tenant);
}
