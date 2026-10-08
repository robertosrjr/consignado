package br.com.consignado.platform.ingestion.infrastructure.adapter.out.persistence;

import br.com.consignado.platform.ingestion.application.port.out.DurableInboxPort;
import br.com.consignado.platform.ingestion.application.port.out.InboxItem;
import br.com.consignado.platform.ingestion.application.port.out.InboxWriteResult;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DurableInbox em memoria, <b>apenas para desenvolvimento e testes</b> (nao e duravel: se perde ao
 * reiniciar e nao e compartilhado com Dispatcher/Worker). Mesma semantica de escrita condicional.
 */
public final class InMemoryInboxAdapter implements DurableInboxPort {

  private final ConcurrentHashMap<IdempotencyKey, InboxItem> items = new ConcurrentHashMap<>();

  @Override
  public InboxWriteResult putIfAbsent(InboxItem item) {
    InboxItem existing = items.putIfAbsent(item.idempotencyKey(), item);
    if (existing == null) {
      return InboxWriteResult.createdNew();
    }
    return InboxWriteResult.alreadyExists(existing.tracingId(), existing.createdAtEpochMillis());
  }

  public int size() {
    return items.size();
  }
}
