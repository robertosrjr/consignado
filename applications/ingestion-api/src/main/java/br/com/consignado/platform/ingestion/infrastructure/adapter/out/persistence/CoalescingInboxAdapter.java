package br.com.consignado.platform.ingestion.infrastructure.adapter.out.persistence;

import br.com.consignado.platform.ingestion.application.port.out.DurableInboxPort;
import br.com.consignado.platform.ingestion.application.port.out.InboxItem;
import br.com.consignado.platform.ingestion.application.port.out.InboxWriteResult;
import br.com.consignado.platform.ingestion.domain.InboxUnavailableException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

/**
 * Protege o DurableInbox da tempestade de duplicatas (a mesma chave disparada centenas de vezes no
 * mesmo instante). Duas camadas, locais a esta instancia:
 *
 * <ol>
 *   <li><b>Coalescencia</b>: a primeira requisicao de uma chave grava; as que chegam enquanto ela
 *       esta em andamento esperam o mesmo resultado em vez de gravar de novo.
 *   <li><b>Memoria curta</b>: depois do sucesso, repeticoes respondem como duplicata sem tocar o
 *       banco, ate o fim do prazo.
 * </ol>
 *
 * <p>O DynamoDB continua sendo a fonte da verdade: o cache so economiza escritas. Chave por
 * parceiro + chave de idempotencia (nada vaza entre parceiros); so sucesso fica guardado (um 503 e
 * repetido); tamanho limitado (cheio, a chamada vai direto ao banco).
 */
public final class CoalescingInboxAdapter implements DurableInboxPort {

  private record Entry(CompletableFuture<InboxWriteResult> result, long expiresAtNanos) {}

  private final DurableInboxPort delegate;
  private final long ttlNanos;
  private final long waitNanos;
  private final int maxEntries;
  private final LongSupplier nanoClock;
  private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

  public CoalescingInboxAdapter(
      DurableInboxPort delegate,
      Duration ttl,
      Duration maxWait,
      int maxEntries,
      LongSupplier nanoClock) {
    this.delegate = delegate;
    this.ttlNanos = ttl.toNanos();
    this.waitNanos = maxWait.toNanos();
    this.maxEntries = maxEntries;
    this.nanoClock = nanoClock;
  }

  @Override
  public InboxWriteResult putIfAbsent(InboxItem item) {
    String cacheKey = item.tenant().value() + '#' + item.idempotencyKey();
    long now = nanoClock.getAsLong();

    Entry existing = entries.get(cacheKey);
    if (existing != null && now - existing.expiresAtNanos() < 0) {
      return awaitAsDuplicate(cacheKey, existing, item);
    }
    if (entries.size() >= maxEntries && !evictExpired(now)) {
      return delegate.putIfAbsent(item);
    }

    Entry mine = new Entry(new CompletableFuture<>(), now + ttlNanos);
    Entry winner =
        entries.compute(
            cacheKey,
            (k, current) -> current != null && now - current.expiresAtNanos() < 0 ? current : mine);
    if (winner != mine) {
      return awaitAsDuplicate(cacheKey, winner, item);
    }
    try {
      InboxWriteResult result = delegate.putIfAbsent(item);
      // Para quem espera, a gravacao desta requisicao e a original
      mine.result()
          .complete(
              result.created()
                  ? InboxWriteResult.alreadyExists(item.tracingId(), item.createdAtEpochMillis())
                  : result);
      return result;
    } catch (RuntimeException e) {
      entries.remove(cacheKey, mine);
      mine.result().completeExceptionally(e);
      throw e;
    }
  }

  private InboxWriteResult awaitAsDuplicate(String cacheKey, Entry entry, InboxItem item) {
    try {
      return entry.result().get(waitNanos, TimeUnit.NANOSECONDS);
    } catch (ExecutionException e) {
      // A gravacao original falhou: esta requisicao tenta por conta propria
      entries.remove(cacheKey, entry);
      return delegate.putIfAbsent(item);
    } catch (TimeoutException e) {
      throw new InboxUnavailableException("DurableInbox lento", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InboxUnavailableException("Espera interrompida", e);
    }
  }

  /** Remove entradas vencidas; retorna {@code true} se abriu espaco. */
  private boolean evictExpired(long now) {
    entries.values().removeIf(e -> now - e.expiresAtNanos() >= 0 && e.result().isDone());
    return entries.size() < maxEntries;
  }
}
