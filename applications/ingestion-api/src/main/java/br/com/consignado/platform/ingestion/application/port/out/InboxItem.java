package br.com.consignado.platform.ingestion.application.port.out;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.shared.vo.TenantId;

/**
 * Item a gravar no DurableInbox. Os atributos de roteamento ficam em claro (o Dispatcher nao
 * precisa decifrar nada); o CPF so existe dentro de {@code protectedPayload}.
 */
public record InboxItem(
    IdempotencyKey idempotencyKey,
    TenantId tenant,
    ConvenioType convenio,
    boolean portability,
    Money amount,
    String protectedPayload,
    long createdAtEpochMillis,
    long ttlEpochSeconds,
    String tracingId,
    BusinessFingerprintPort.Fingerprints fingerprints) {

  public InboxItem(
      IdempotencyKey key,
      TenantId tenant,
      ConvenioType convenio,
      boolean portability,
      Money amount,
      String protectedPayload,
      long createdAt,
      long ttl,
      String tracingId) {
    this(
        key,
        tenant,
        convenio,
        portability,
        amount,
        protectedPayload,
        createdAt,
        ttl,
        tracingId,
        null);
  }

  /** Nunca imprime o payload protegido. */
  @Override
  public String toString() {
    return "InboxItem[tenant=" + tenant + ", convenio=" + convenio + "]";
  }
}
