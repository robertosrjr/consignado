package br.com.consignado.platform.dispatcher.metrics;

import br.com.consignado.platform.dispatcher.dispatch.BatchResult;
import java.io.PrintStream;
import java.time.Clock;

/**
 * Metricas do Dispatcher no formato CloudWatch Embedded Metric Format (EMF): uma linha JSON por
 * metrica em stdout, que o CloudWatch converte em metricas sem chamar a API (sem NAT, sem custo de
 * PutMetricData). Contem somente contagens e nomes fixos de lane (nenhum dado de proposta).
 */
public final class EmfMetrics {

  static final String NAMESPACE = "ConsignadoPoc/Dispatcher";

  private final PrintStream out;
  private final Clock clock;

  public EmfMetrics(PrintStream out, Clock clock) {
    this.out = out;
    this.clock = clock;
  }

  public void publish(BatchResult result) {
    laneMetric("FAST", result.dispatchedFast());
    laneMetric("STANDARD", result.dispatchedStandard());
    out.println(plainMetric("RecordsSkipped", result.skipped()));
    out.println(plainMetric("RecordsFailed", result.failed()));
  }

  private void laneMetric(String lane, int count) {
    if (count == 0) {
      return;
    }
    out.println(
        "{\"_aws\":{\"Timestamp\":"
            + clock.millis()
            + ",\"CloudWatchMetrics\":[{\"Namespace\":\""
            + NAMESPACE
            + "\",\"Dimensions\":[[\"Lane\"]],\"Metrics\":[{\"Name\":\"RecordsDispatched\","
            + "\"Unit\":\"Count\"}]}]},\"Lane\":\""
            + lane
            + "\",\"RecordsDispatched\":"
            + count
            + "}");
  }

  private String plainMetric(String name, int count) {
    return "{\"_aws\":{\"Timestamp\":"
        + clock.millis()
        + ",\"CloudWatchMetrics\":[{\"Namespace\":\""
        + NAMESPACE
        + "\",\"Dimensions\":[[]],\"Metrics\":[{\"Name\":\""
        + name
        + "\",\"Unit\":\"Count\"}]}]},\""
        + name
        + "\":"
        + count
        + "}";
  }
}
