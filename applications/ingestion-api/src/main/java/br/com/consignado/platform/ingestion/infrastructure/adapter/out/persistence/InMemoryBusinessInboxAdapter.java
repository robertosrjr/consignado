package br.com.consignado.platform.ingestion.infrastructure.adapter.out.persistence;

import br.com.consignado.platform.ingestion.application.port.out.DurableInboxPort;
import br.com.consignado.platform.ingestion.application.port.out.InboxItem;
import br.com.consignado.platform.ingestion.application.port.out.InboxWriteResult;
import br.com.consignado.platform.ingestion.domain.IdempotencyMismatchException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Test/development only. Synchronization emulates the two-table transaction, without durability.
 */
public final class InMemoryBusinessInboxAdapter implements DurableInboxPort {
  private record Stored(
      String tenant, String request, String proposal, String trace, long createdAt) {}

  private final Map<String, Stored> headers = new HashMap<>();
  private final Map<String, Stored> businesses = new HashMap<>();

  @Override
  public synchronized InboxWriteResult putIfAbsent(InboxItem item) {
    String headerKey = item.tenant().value() + "#" + item.idempotencyKey();
    Stored header = headers.get(headerKey);
    if (header != null) {
      if (!header.request().equals(item.fingerprints().request())) {
        throw new IdempotencyMismatchException();
      }
      return duplicate(header, false);
    }
    Stored business = businesses.get(item.fingerprints().business());
    if (business != null) {
      if (!business.tenant().equals(item.tenant().value())) {
        throw new IdempotencyMismatchException();
      }
      headers.put(
          headerKey,
          new Stored(
              business.tenant(),
              item.fingerprints().request(),
              business.proposal(),
              business.trace(),
              business.createdAt()));
      return duplicate(business, true);
    }
    Stored stored =
        new Stored(
            item.tenant().value(),
            item.fingerprints().request(),
            UUID.randomUUID().toString(),
            item.tracingId(),
            item.createdAtEpochMillis());
    headers.put(headerKey, stored);
    businesses.put(item.fingerprints().business(), stored);
    return new InboxWriteResult(true, null, null, stored.proposal(), false);
  }

  private static InboxWriteResult duplicate(Stored stored, boolean semantic) {
    return new InboxWriteResult(
        false, stored.trace(), stored.createdAt(), stored.proposal(), semantic);
  }
}
