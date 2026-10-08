package br.com.consignado.platform.worker.infrastructure.metrics;

/**
 * Estima o crescimento liquido do backlog e o tempo efetivo de drenagem a partir de amostras
 * periodicas da profundidade das filas e do total processado. Com lambda = taxa de chegada e mu =
 * taxa de processamento: {@code net_backlog_growth = lambda - mu} (a variacao da profundidade) e
 * {@code effective_drain_time = profundidade / mu}.
 */
public final class BacklogEstimator {

  private boolean initialized;
  private long lastDepth;
  private long lastProcessed;
  private long lastNanos;
  private double processingRate;
  private double arrivalRate;
  private double netGrowth;
  private double drainSeconds;

  public synchronized void sample(long totalDepth, long processedTotal, long nowNanos) {
    if (!initialized) {
      initialized = true;
      lastDepth = totalDepth;
      lastProcessed = processedTotal;
      lastNanos = nowNanos;
      return;
    }
    double seconds = (nowNanos - lastNanos) / 1_000_000_000.0;
    if (seconds <= 0) {
      return;
    }
    processingRate = (processedTotal - lastProcessed) / seconds;
    double depthChange = (totalDepth - lastDepth) / seconds;
    arrivalRate = Math.max(0.0, processingRate + depthChange);
    netGrowth = arrivalRate - processingRate;
    if (totalDepth <= 0) {
      drainSeconds = 0.0;
    } else if (processingRate > 0) {
      drainSeconds = totalDepth / processingRate;
    } else {
      drainSeconds = Double.POSITIVE_INFINITY;
    }
    lastDepth = totalDepth;
    lastProcessed = processedTotal;
    lastNanos = nowNanos;
  }

  public synchronized double processingRate() {
    return processingRate;
  }

  public synchronized double arrivalRate() {
    return arrivalRate;
  }

  public synchronized double netBacklogGrowth() {
    return netGrowth;
  }

  public synchronized double effectiveDrainTimeSeconds() {
    return drainSeconds;
  }
}
