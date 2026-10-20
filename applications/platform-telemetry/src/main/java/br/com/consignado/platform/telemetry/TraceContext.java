package br.com.consignado.platform.telemetry;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.HashMap;
import java.util.Map;

/** Only W3C trace context crosses boundaries; baggage and business payloads are excluded. */
public final class TraceContext {
  private static final W3CTraceContextPropagator PROPAGATOR =
      W3CTraceContextPropagator.getInstance();
  private static final TextMapGetter<Map<String, String>> GETTER =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
          return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
          return carrier.get(key);
        }
      };

  private TraceContext() {}

  public static Map<String, String> capture() {
    Map<String, String> carrier = new HashMap<>();
    PROPAGATOR.inject(Context.current(), carrier, Map::put);
    return Map.copyOf(carrier);
  }

  public static Context extract(Map<String, String> carrier) {
    return PROPAGATOR.extract(Context.root(), carrier == null ? Map.of() : carrier, GETTER);
  }
}
