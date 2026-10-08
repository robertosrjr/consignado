package br.com.consignado.platform.worker.infrastructure.adapter.out.inbox;

import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.worker.application.port.out.InboxReadPort;
import br.com.consignado.platform.worker.application.port.out.InboxRecord;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Leitura do inbox em memoria, <b>apenas para desenvolvimento e testes</b> (itens semeados). */
public final class InMemoryInboxReadAdapter implements InboxReadPort {

  private final ConcurrentHashMap<IdempotencyKey, InboxRecord> records = new ConcurrentHashMap<>();

  public void put(InboxRecord record) {
    records.put(record.key(), record);
  }

  @Override
  public Optional<InboxRecord> load(IdempotencyKey key) {
    return Optional.ofNullable(records.get(key));
  }
}
