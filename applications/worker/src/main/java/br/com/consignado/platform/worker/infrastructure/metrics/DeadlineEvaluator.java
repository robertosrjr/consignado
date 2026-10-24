package br.com.consignado.platform.worker.infrastructure.metrics;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.worker.application.port.out.DeadlineIndexPort;
import br.com.consignado.platform.worker.application.port.out.DeadlineIndexPort.OpenProposal;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.SmartLifecycle;

/**
 * Avaliador do SLO J2: a cada ciclo, classifica as propostas cujo prazo ({@code createdAt + prazo
 * da lane}) venceu. Boa se decidida ate o prazo; ruim se decidida depois ou ainda sem decisao.
 * Emite {@code slo.j2.events{lane, outcome}} uma vez por proposta, sem labels de identidade.
 *
 * <p>O estado vem do DurableInbox, nao da memoria do processo: propostas na fila, na DLQ ou com o
 * Worker parado tambem vencem e contam.
 */
public final class DeadlineEvaluator implements SmartLifecycle {

  private static final Logger LOG = System.getLogger(DeadlineEvaluator.class.getName());

  static final String GOOD = "good";
  static final String BAD = "bad";

  private final DeadlineIndexPort index;
  private final Clock clock;
  private final Map<Lane, Duration> deadlines;
  private final Duration period;
  private final int batchSize;
  private final Map<Lane, Counter> good = new EnumMap<>(Lane.class);
  private final Map<Lane, Counter> bad = new EnumMap<>(Lane.class);
  private final AtomicLong lastSuccessMillis = new AtomicLong();
  private ScheduledExecutorService scheduler;
  private volatile boolean running;

  public DeadlineEvaluator(
      MeterRegistry registry,
      DeadlineIndexPort index,
      Clock clock,
      Map<Lane, Duration> deadlines,
      Duration period,
      int batchSize) {
    this.index = index;
    this.clock = clock;
    this.deadlines = Map.copyOf(deadlines);
    this.period = period;
    this.batchSize = batchSize;
    for (Lane lane : Lane.values()) {
      good.put(lane, counter(registry, lane, GOOD));
      bad.put(lane, counter(registry, lane, BAD));
    }
    // 0 ate o primeiro ciclo completo: para o alerta de frescor, "nunca rodou" e o mais antigo
    Gauge.builder("slo.j2.last.evaluation", lastSuccessMillis, m -> m.get() / 1000.0)
        .description("Instante (epoch, s) do ultimo ciclo completo do avaliador de prazos")
        .baseUnit("seconds")
        .register(registry);
  }

  /** Um ciclo de avaliacao (publico para testes). */
  public void evaluateNow() {
    long now = clock.millis();
    for (Lane lane : Lane.values()) {
      long deadline = deadlines.get(lane).toMillis();
      for (OpenProposal open : index.acceptedUpTo(lane, now - deadline, batchSize)) {
        Long decidedAt = open.decidedAtEpochMillis();
        boolean onTime = decidedAt != null && decidedAt <= open.createdAtEpochMillis() + deadline;
        if (index.close(open.key(), onTime ? GOOD : BAD)) {
          (onTime ? good : bad).get(lane).increment();
        }
      }
    }
    lastSuccessMillis.set(now);
  }

  @Override
  public void start() {
    scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
    scheduler.scheduleWithFixedDelay(
        this::evaluateSafely, period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
    running = true;
  }

  @Override
  public void stop() {
    running = false;
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private void evaluateSafely() {
    try {
      evaluateNow();
    } catch (RuntimeException e) {
      // sem fechamento nada e contado: o proximo ciclo retoma os mesmos itens
      LOG.log(
          Level.WARNING, "event=slo_deadline_evaluation_failed error={0}", e.getClass().getName());
    }
  }

  private static Counter counter(MeterRegistry registry, Lane lane, String outcome) {
    return Counter.builder("slo.j2.events")
        .description("Propostas avaliadas contra o prazo do SLO J2, uma vez por proposta")
        .tag("lane", lane.name().toLowerCase(Locale.ROOT))
        .tag("outcome", outcome)
        .register(registry);
  }
}
