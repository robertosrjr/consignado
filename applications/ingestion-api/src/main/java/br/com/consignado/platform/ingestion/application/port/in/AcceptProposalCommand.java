package br.com.consignado.platform.ingestion.application.port.in;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.vo.Cpf;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.shared.vo.TenantId;

/** Pedido de aceite de uma proposta, ja com os value objects validados. */
public record AcceptProposalCommand(
    IdempotencyKey idempotencyKey,
    TenantId tenant,
    Cpf cpf,
    ConvenioType convenio,
    boolean portability,
    Money amount,
    int installments,
    java.time.LocalDate contractDate) {
  public AcceptProposalCommand(
      IdempotencyKey key,
      TenantId tenant,
      Cpf cpf,
      ConvenioType convenio,
      boolean portability,
      Money amount,
      int installments) {
    this(key, tenant, cpf, convenio, portability, amount, installments, null);
  }
}
