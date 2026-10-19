package br.com.consignado.platform.legacy.config;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;

/** Sobreposicao temporaria; expiracao independe do cliente e de tarefas agendadas. */
@Component
public class LegacyFaultPolicy {

  private final LegacyProperties.Chaos baseline;
  private final LongSupplier clock;
  private LegacyProperties.Chaos override;
  private long startedAt;
  private long durationNanos;

  public LegacyFaultPolicy(LegacyProperties properties, LongSupplier legacyClock) {
    this.baseline = properties.chaos();
    this.clock = legacyClock;
  }

  public synchronized State state() {
    long remaining = 0;
    if (override != null) {
      remaining = durationNanos - (clock.getAsLong() - startedAt);
      if (remaining <= 0) {
        override = null;
      }
    }
    var effective = override == null ? baseline : override;
    return new State(
        override != null,
        effective.latencyMs(),
        effective.errorRate(),
        override == null ? 0 : TimeUnit.NANOSECONDS.toMillis(remaining));
  }

  public synchronized State activate(long latencyMs, double errorRate, long durationMs) {
    if (latencyMs < 0
        || latencyMs > 10_000
        || !Double.isFinite(errorRate)
        || errorRate < 0
        || errorRate > 1
        || durationMs < 1
        || durationMs > 300_000
        || (latencyMs == 0 && errorRate == 0)) {
      throw new IllegalArgumentException("Parametros de falha fora dos limites");
    }
    if (state().active()) {
      throw new IllegalStateException("Ja existe uma falha ativa");
    }
    override = new LegacyProperties.Chaos(latencyMs, errorRate);
    durationNanos = TimeUnit.MILLISECONDS.toNanos(durationMs);
    startedAt = clock.getAsLong();
    return state();
  }

  public synchronized State restore() {
    override = null;
    return state();
  }

  public record State(boolean active, long latencyMs, double errorRate, long remainingMs) {}
}
