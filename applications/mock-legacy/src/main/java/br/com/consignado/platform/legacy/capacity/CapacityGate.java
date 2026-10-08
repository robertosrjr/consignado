package br.com.consignado.platform.legacy.capacity;

import java.util.function.LongSupplier;

/**
 * Janela deslizante de 1 segundo (meio-aberta: (agora - 1 s, agora]) que aceita no maximo {@code
 * limit} entradas. Garante que o legado simulado nunca registre mais que o limite em qualquer
 * janela de 1 segundo; o excedente e rejeitado (HTTP 503).
 */
public final class CapacityGate {

  private static final long WINDOW_NANOS = 1_000_000_000L;

  private final int limit;
  private final LongSupplier nanoClock;
  private final long[] ring;
  private int head;
  private int size;
  private int peak;

  public CapacityGate(int limit, LongSupplier nanoClock) {
    if (limit < 1) {
      throw new IllegalArgumentException("Limite de capacidade invalido");
    }
    this.limit = limit;
    this.nanoClock = nanoClock;
    this.ring = new long[limit];
  }

  /** Tenta registrar uma entrada; retorna {@code false} se a janela atual ja esta cheia. */
  public synchronized boolean tryEnter() {
    long now = nanoClock.getAsLong();
    evictExpired(now);
    if (size >= limit) {
      return false;
    }
    ring[(head + size) % limit] = now;
    size++;
    if (size > peak) {
      peak = size;
    }
    return true;
  }

  /** Entradas aceitas na janela atual de 1 segundo. */
  public synchronized int current() {
    evictExpired(nanoClock.getAsLong());
    return size;
  }

  /** Maior ocupacao de janela ja observada (nunca excede o limite). */
  public synchronized int peak() {
    return peak;
  }

  public int limit() {
    return limit;
  }

  private void evictExpired(long now) {
    while (size > 0 && now - ring[head] >= WINDOW_NANOS) {
      head = (head + 1) % limit;
      size--;
    }
  }
}
