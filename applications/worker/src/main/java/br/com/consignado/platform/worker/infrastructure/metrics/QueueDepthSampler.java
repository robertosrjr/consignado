package br.com.consignado.platform.worker.infrastructure.metrics;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.worker.application.port.out.QueueDepthPort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.SmartLifecycle;

/** Amostra periodicamente a profundidade das filas e alimenta o estimador de backlog. */
public final class QueueDepthSampler implements SmartLifecycle {

  private final QueueDepthPort depths;
  private final WorkerMetrics metrics;
  private final BacklogEstimator estimator;
  private final long periodSeconds;
  private final AtomicLong fastDepth = new AtomicLong();
  private final AtomicLong standardDepth = new AtomicLong();
  private final AtomicLong dlqDepth = new AtomicLong(-1);
  private ScheduledExecutorService scheduler;
  private volatile boolean running;

  public QueueDepthSampler(
      MeterRegistry registry,
      QueueDepthPort depths,
      WorkerMetrics metrics,
      BacklogEstimator estimator,
      long periodSeconds) {
    this.depths = depths;
    this.metrics = metrics;
    this.estimator = estimator;
    this.periodSeconds = periodSeconds;
    for (Lane lane : Lane.values()) {
      AtomicLong holder = lane == Lane.FAST ? fastDepth : standardDepth;
      Gauge.builder("worker.queue.depth", holder, AtomicLong::get)
          .description("Profundidade aproximada da fila da lane")
          .tag("lane", lane.name().toLowerCase(Locale.ROOT))
          .register(registry);
    }
    // NaN enquanto nao houver amostra valida: ausencia de dado nao vira DLQ vazia
    Gauge.builder("worker.dlq.depth", dlqDepth, d -> d.get() < 0 ? Double.NaN : d.get())
        .description("Profundidade aproximada da DLQ das lanes")
        .register(registry);
  }

  /** Uma amostragem (publica para testes). */
  public void sampleNow(long nanoTime) {
    long fast = depths.approximateDepth(Lane.FAST);
    long standard = depths.approximateDepth(Lane.STANDARD);
    fastDepth.set(fast);
    standardDepth.set(standard);
    dlqDepth.set(depths.dlqDepth());
    estimator.sample(fast + standard, metrics.completedTotal(), nanoTime);
  }

  @Override
  public void start() {
    scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
    scheduler.scheduleAtFixedRate(
        () -> sampleNow(System.nanoTime()), 0, periodSeconds, TimeUnit.SECONDS);
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
}
