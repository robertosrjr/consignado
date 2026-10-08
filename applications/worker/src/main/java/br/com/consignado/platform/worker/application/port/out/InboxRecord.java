package br.com.consignado.platform.worker.application.port.out;

import br.com.consignado.platform.shared.payload.InboxPayload;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;

/** Item lido do DurableInbox, ja decifrado. {@code toString} nao imprime o payload. */
public record InboxRecord(
    IdempotencyKey key,
    TenantId tenant,
    InboxPayload payload,
    String tracingId,
    Long receivedAtEpochMillis) {

  public InboxRecord(IdempotencyKey key, TenantId tenant, InboxPayload payload, String tracingId) {
    this(key, tenant, payload, tracingId, null);
  }

  @Override
  public String toString() {
    return "InboxRecord[tenant=" + tenant + "]";
  }
}
