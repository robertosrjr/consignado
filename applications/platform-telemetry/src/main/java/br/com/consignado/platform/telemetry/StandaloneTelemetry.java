package br.com.consignado.platform.telemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.time.Duration;
import java.util.Map;

/** Non-Spring dispatcher lifecycle. No network exporter unless explicitly configured. */
public final class StandaloneTelemetry {
  private StandaloneTelemetry() {}

  public static OpenTelemetrySdk create(String service, Map<String, String> env) {
    double sampling = Double.parseDouble(env.getOrDefault("PLATFORM_TRACING_SAMPLING", "1.0"));
    var provider =
        SdkTracerProvider.builder()
            .setResource(
                Resource.getDefault()
                    .merge(
                        Resource.create(
                            Attributes.of(
                                AttributeKey.stringKey("service.name"),
                                service,
                                AttributeKey.stringKey("deployment.environment.name"),
                                env.getOrDefault("PLATFORM_ENV", "local")))))
            .setSampler(Sampler.parentBased(Sampler.traceIdRatioBased(sampling)));
    String endpoint = env.get("PLATFORM_OTLP_TRACES_ENDPOINT");
    if (endpoint != null && !endpoint.isBlank()) {
      var exporter =
          OtlpHttpSpanExporter.builder()
              .setEndpoint(endpoint)
              .setTimeout(Duration.ofSeconds(2))
              .build();
      provider.addSpanProcessor(BatchSpanProcessor.builder(exporter).build());
    }
    return OpenTelemetrySdk.builder()
        .setTracerProvider(provider.build())
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .build();
  }
}
