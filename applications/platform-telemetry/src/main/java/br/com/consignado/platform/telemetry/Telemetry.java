package br.com.consignado.platform.telemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.Objects;
import org.slf4j.MDC;

/** Infrastructure spans share the Spring SDK, or the standalone dispatcher SDK. */
public final class Telemetry {
  private static volatile OpenTelemetry telemetry = OpenTelemetry.noop();

  private Telemetry() {}

  public static void configure(OpenTelemetry value) {
    telemetry = Objects.requireNonNull(value);
  }

  public static Operation start(String name, SpanKind kind) {
    return start(name, kind, Context.current());
  }

  public static Operation start(String name, SpanKind kind, Context parent) {
    var builder =
        telemetry
            .getTracer("consignado.infrastructure", "1.0")
            .spanBuilder(name)
            .setSpanKind(kind)
            .setParent(parent);
    var origin = Span.fromContext(parent).getSpanContext();
    if (kind == SpanKind.CONSUMER && origin.isValid()) {
      builder.addLink(origin);
    }
    return new Operation(builder.startSpan());
  }

  public static final class Operation implements AutoCloseable {
    private final Span span;
    private final Scope scope;
    private final String previousTrace;
    private final String previousSpan;

    private Operation(Span span) {
      this.span = span;
      previousTrace = MDC.get("traceId");
      previousSpan = MDC.get("spanId");
      scope = span.makeCurrent();
      if (span.getSpanContext().isValid()) {
        MDC.put("traceId", span.getSpanContext().getTraceId());
        MDC.put("spanId", span.getSpanContext().getSpanId());
      }
    }

    public Span span() {
      return span;
    }

    public void failed(Throwable failure) {
      // Exception messages/stack traces may contain customer data or credentials.
      span.setStatus(StatusCode.ERROR);
      span.setAttribute("error.type", failure.getClass().getName());
      span.addEvent(
          "exception",
          io.opentelemetry.api.common.Attributes.of(
              io.opentelemetry.api.common.AttributeKey.stringKey("exception.type"),
              failure.getClass().getName()));
    }

    @Override
    public void close() {
      try {
        scope.close();
      } finally {
        restore("traceId", previousTrace);
        restore("spanId", previousSpan);
        span.end();
      }
    }

    private static void restore(String key, String value) {
      if (value == null) {
        MDC.remove(key);
      } else {
        MDC.put(key, value);
      }
    }
  }
}
