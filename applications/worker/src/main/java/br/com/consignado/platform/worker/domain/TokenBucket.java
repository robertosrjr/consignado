package br.com.consignado.platform.worker.domain;

import java.util.function.LongSupplier;

/**
 * Balde de permissoes com reposicao continua. Em qualquer janela de duracao T, no maximo {@code
 * capacity + rate * T} permissoes sao concedidas; com capacidade pequena e taxa levemente abaixo do
 * limite do legado, isso garante que nenhuma janela de 1 segundo ultrapasse o teto (180 TPS).
 */
public final class TokenBucket {

  private final double permitsPerNano;
  private final double capacity;
  private final LongSupplier nanoClock;
  private double tokens;
  private long lastRefill;

  public TokenBucket(double permitsPerSecond, double capacity, LongSupplier nanoClock) {
    if (permitsPerSecond <= 0 || capacity < 1) {
      throw new IllegalArgumentException("Balde invalido");
    }
    this.permitsPerNano = permitsPerSecond / 1_000_000_000.0;
    this.capacity = capacity;
    this.nanoClock = nanoClock;
    this.tokens = capacity;
    this.lastRefill = nanoClock.getAsLong();
  }

  /** Permissoes disponiveis agora (apos reposicao). */
  public synchronized double tokens() {
    refill();
    return tokens;
  }

  /** Consome uma permissao, se houver. */
  public synchronized boolean tryConsume() {
    refill();
    if (tokens >= 1.0) {
      tokens -= 1.0;
      return true;
    }
    return false;
  }

  private void refill() {
    long now = nanoClock.getAsLong();
    long elapsed = now - lastRefill;
    if (elapsed > 0) {
      tokens = Math.min(capacity, tokens + elapsed * permitsPerNano);
      lastRefill = now;
    }
  }
}
