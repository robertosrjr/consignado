package br.com.consignado.platform.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TraceContextTest {
  private final List<SpanData> spans = new ArrayList<>();
  private OpenTelemetrySdk sdk;

  @BeforeEach
  void setup() {
    SpanExporter exporter =
        new SpanExporter() {
          public CompletableResultCode export(Collection<SpanData> values) {
            spans.addAll(values);
            return CompletableResultCode.ofSuccess();
          }

          public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
          }

          public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
          }
        };
    sdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build())
            .build();
    Telemetry.configure(sdk);
  }

  @AfterEach
  void cleanup() {
    Telemetry.configure(OpenTelemetry.noop());
    sdk.close();
    MDC.clear();
  }

  @Test
  void should_continue_context_on_another_thread_without_leaking_mdc() throws Exception {
    MDC.put("traceId", "previous");
    Map<String, String> carrier;
    String trace;
    String parent;
    try (var operation = Telemetry.start("accept", SpanKind.SERVER)) {
      carrier = TraceContext.capture();
      trace = operation.span().getSpanContext().getTraceId();
      parent = operation.span().getSpanContext().getSpanId();
      assertThat(MDC.get("traceId")).isEqualTo(trace);
    }
    assertThat(MDC.get("traceId")).isEqualTo("previous");
    try (var executor = Executors.newSingleThreadExecutor()) {
      executor
          .submit(
              () -> {
                try (var operation =
                    Telemetry.start("process", SpanKind.CONSUMER, TraceContext.extract(carrier))) {
                  assertThat(operation.span().getSpanContext().getTraceId()).isEqualTo(trace);
                  assertThat(MDC.get("spanId"))
                      .isEqualTo(operation.span().getSpanContext().getSpanId());
                }
                assertThat(MDC.get("traceId")).isNull();
                assertThat(Span.current().getSpanContext().isValid()).isFalse();
              })
          .get();
    }
    SpanData consumer =
        spans.stream().filter(s -> s.getName().equals("process")).findFirst().orElseThrow();
    assertThat(consumer.getParentSpanId()).isEqualTo(parent);
    assertThat(consumer.getLinks()).hasSize(1);
  }

  @Test
  void should_drop_baggage_and_invalid_context() {
    assertThat(
            Span.fromContext(
                    TraceContext.extract(Map.of("traceparent", "invalid", "baggage", "cpf=secret")))
                .getSpanContext()
                .isValid())
        .isFalse();
    try (var operation = Telemetry.start("send", SpanKind.PRODUCER)) {
      assertThat(operation.span().getSpanContext().isValid()).isTrue();
      assertThat(TraceContext.capture()).containsOnlyKeys("traceparent");
    }
    assertThat(TraceContext.capture()).isEmpty();
    assertThat(Span.fromContext(TraceContext.extract(null)).getSpanContext().isValid()).isFalse();
  }

  @Test
  void should_record_error_type_without_sensitive_exception_message() {
    try (var operation = Telemetry.start("http", SpanKind.CLIENT)) {
      operation.failed(new IllegalStateException("cpf=12345678901 password=secret"));
    }
    SpanData data = spans.getFirst();
    assertThat(data.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(data.getAttributes().toString()).doesNotContain("12345678901", "secret");
    assertThat(data.getEvents().toString()).doesNotContain("12345678901", "secret");
    assertThat(data.getEvents()).hasSize(1);
  }

  @Test
  void should_preserve_unsampled_remote_parent_and_tracestate() {
    String traceparent = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-00";
    try (var operation =
        Telemetry.start(
            "process",
            SpanKind.CONSUMER,
            TraceContext.extract(
                Map.of("traceparent", traceparent, "tracestate", "vendor=value")))) {
      assertThat(operation.span().getSpanContext().isSampled()).isFalse();
      assertThat(TraceContext.capture()).containsEntry("tracestate", "vendor=value");
    }
    assertThat(spans).isEmpty();
  }

  @Test
  void should_create_standalone_sdk_without_network_exporter() {
    try (var standalone =
        StandaloneTelemetry.create("dispatcher-test", Map.of("PLATFORM_TRACING_SAMPLING", "0"))) {
      Span span = standalone.getTracer("test").spanBuilder("local").startSpan();
      assertThat(span.getSpanContext().isSampled()).isFalse();
      span.end();
    }
  }
}
