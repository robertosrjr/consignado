package br.com.consignado.platform.worker.application.usecase;

import java.time.Duration;

/** Backoff exponencial limitado para devolver mensagens a fila apos falha transitoria. */
public final class RetryBackoff {

  private final Duration base;
  private final Duration max;

  public RetryBackoff(Duration base, Duration max) {
    if (base.isNegative() || max.compareTo(base) < 0) {
      throw new IllegalArgumentException("Backoff invalido");
    }
    this.base = base;
    this.max = max;
  }

  /** Atraso para a n-esima entrega (1 = primeira): base * 2^(n-1), limitado a {@code max}. */
  public Duration delay(int receiveCount) {
    int exponent = Math.min(Math.max(receiveCount, 1) - 1, 20);
    Duration delay = base.multipliedBy(1L << exponent);
    return delay.compareTo(max) > 0 ? max : delay;
  }
}
