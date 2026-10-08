package br.com.consignado.platform.legacy.ledger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registro de averbacoes por chave de idempotencia. A primeira chamada com uma chave cria a
 * averbacao; repeticoes devolvem o mesmo identificador e sao contadas como duplicatas (o DoD espera
 * zero averbacoes duplicadas). Memoria limitada: ao exceder o maximo, as chaves mais antigas sao
 * esquecidas (FIFO).
 */
public final class AverbacaoLedger {

  /** Resultado de um registro. */
  public record Registration(UUID averbacaoId, boolean duplicate) {}

  private final int maxEntries;
  private final ConcurrentHashMap<UUID, UUID> entries = new ConcurrentHashMap<>();
  private final ConcurrentLinkedQueue<UUID> order = new ConcurrentLinkedQueue<>();
  private final AtomicLong unique = new AtomicLong();
  private final AtomicLong duplicate = new AtomicLong();

  public AverbacaoLedger(int maxEntries) {
    if (maxEntries < 1) {
      throw new IllegalArgumentException("Tamanho maximo invalido");
    }
    this.maxEntries = maxEntries;
  }

  public Registration register(UUID idempotencyKey) {
    UUID candidate = UUID.nameUUIDFromBytes(idempotencyKey.toString().getBytes());
    UUID existing = entries.putIfAbsent(idempotencyKey, candidate);
    if (existing != null) {
      duplicate.incrementAndGet();
      return new Registration(existing, true);
    }
    unique.incrementAndGet();
    order.add(idempotencyKey);
    while (entries.size() > maxEntries) {
      UUID oldest = order.poll();
      if (oldest == null) {
        break;
      }
      entries.remove(oldest);
    }
    return new Registration(candidate, false);
  }

  public long uniqueCount() {
    return unique.get();
  }

  public long duplicateCount() {
    return duplicate.get();
  }

  public int size() {
    return entries.size();
  }
}
