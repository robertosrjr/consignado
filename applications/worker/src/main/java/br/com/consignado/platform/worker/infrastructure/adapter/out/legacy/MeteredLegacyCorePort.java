package br.com.consignado.platform.worker.infrastructure.adapter.out.legacy;

import br.com.consignado.platform.worker.application.port.out.LegacyCorePort;
import br.com.consignado.platform.worker.application.port.out.LegacySubmission;
import br.com.consignado.platform.worker.domain.LegacyResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Decorador de metricas: latencia e resultado das chamadas ao legado ({@code worker.legacy.call}).
 */
public final class MeteredLegacyCorePort implements LegacyCorePort {

  private final LegacyCorePort delegate;
  private final MeterRegistry registry;

  public MeteredLegacyCorePort(LegacyCorePort delegate, MeterRegistry registry) {
    this.delegate = delegate;
    this.registry = registry;
  }

  @Override
  public LegacyResult submit(LegacySubmission submission) {
    Timer.Sample sample = Timer.start(registry);
    LegacyResult result = null;
    try {
      result = delegate.submit(submission);
      return result;
    } finally {
      sample.stop(
          Timer.builder("worker.legacy.call")
              .description("Chamadas do Worker ao legado")
              .tag("outcome", outcome(result))
              .publishPercentileHistogram()
              .register(registry));
    }
  }

  private static String outcome(LegacyResult result) {
    return switch (result) {
      case LegacyResult.Confirmed confirmed -> "confirmed";
      case LegacyResult.Rejected rejected -> "rejected";
      case LegacyResult.TransientFailure transientFailure -> "transient_failure";
      case null -> "error";
    };
  }
}
