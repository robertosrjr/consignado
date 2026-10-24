package br.com.consignado.platform.dispatcher.dispatch;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Duration;

/**
 * Reconciliacao do despacho: itens que ficaram RECEIVED alem da carencia (registro do Stream
 * perdido ou SQS indisponivel no despacho) sao despachados de novo pelo mesmo {@link
 * DispatchService}. Uma corrida com o Stream pode publicar a mesma mensagem duas vezes; o Worker e
 * idempotente por chave, entao a reentrega nao gera segunda averbacao.
 */
public final class OrphanSweeper {

  private static final Logger LOG = System.getLogger(OrphanSweeper.class.getName());

  /** Resultado de uma varredura (sem identificadores). */
  public record SweepResult(int found, int redispatched, int failed) {}

  private final PendingDispatchReader reader;
  private final DispatchService dispatcher;
  private final Clock clock;
  private final Duration grace;
  private final int batchSize;

  public OrphanSweeper(
      PendingDispatchReader reader,
      DispatchService dispatcher,
      Clock clock,
      Duration grace,
      int batchSize) {
    this.reader = reader;
    this.dispatcher = dispatcher;
    this.clock = clock;
    this.grace = grace;
    this.batchSize = batchSize;
  }

  public SweepResult sweep() {
    var pending = reader.pendingCreatedUpTo(clock.millis() - grace.toMillis(), batchSize);
    int redispatched = 0;
    int failed = 0;
    for (StreamRecordView orphan : pending) {
      try {
        if (dispatcher.dispatch(orphan).kind() == DispatchOutcome.Kind.DISPATCHED) {
          redispatched++;
        }
      } catch (RuntimeException e) {
        failed++; // permanece marcado; a proxima varredura tenta de novo
        LOG.log(Level.WARNING, "event=orphan_redispatch_failed error={0}", e.getClass().getName());
      }
    }
    if (!pending.isEmpty()) {
      LOG.log(
          Level.INFO,
          "event=orphan_sweep found={0} redispatched={1} failed={2}",
          pending.size(),
          redispatched,
          failed);
    }
    return new SweepResult(pending.size(), redispatched, failed);
  }
}
