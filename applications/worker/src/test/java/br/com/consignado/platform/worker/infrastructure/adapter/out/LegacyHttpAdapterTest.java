package br.com.consignado.platform.worker.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.vo.Cpf;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.worker.application.port.out.LegacySubmission;
import br.com.consignado.platform.worker.domain.LegacyResult;
import br.com.consignado.platform.worker.infrastructure.adapter.out.legacy.LegacyHttpAdapter;
import br.com.consignado.platform.worker.support.StubLegacy;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LegacyHttpAdapterTest {
  @Test
  void should_propagate_w3c_context_to_real_http_server() throws Exception {
    var header = new java.util.concurrent.atomic.AtomicReference<String>();
    var server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/averbacoes",
        exchange -> {
          header.set(exchange.getRequestHeaders().getFirst("traceparent"));
          byte[] body =
              "{\"averbacaoId\":\"confirmed\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try (var sdk =
        br.com.consignado.platform.telemetry.StandaloneTelemetry.create(
            "test", java.util.Map.of())) {
      br.com.consignado.platform.telemetry.Telemetry.configure(sdk);
      try (var operation =
          br.com.consignado.platform.telemetry.Telemetry.start(
              "process", io.opentelemetry.api.trace.SpanKind.CONSUMER)) {
        var client =
            new LegacyHttpAdapter(
                HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "test",
                Duration.ofSeconds(5),
                breaker(20, 20, 50f));
        assertThat(client.submit(submission(KEY))).isInstanceOf(LegacyResult.Confirmed.class);
        assertThat(header.get()).contains(operation.span().getSpanContext().getTraceId());
        assertThat(header.get()).doesNotContain(operation.span().getSpanContext().getSpanId());
      }
    } finally {
      br.com.consignado.platform.telemetry.Telemetry.configure(
          io.opentelemetry.api.OpenTelemetry.noop());
      server.stop(0);
    }
  }

  private static final String KEY = "e3b0c442-98fc-11ee-b9d1-0242ac120002";

  private StubLegacy stub;

  @BeforeEach
  void start() {
    stub = StubLegacy.start();
  }

  @AfterEach
  void stop() {
    stub.close();
  }

  private static LegacySubmission submission(String key) {
    return new LegacySubmission(
        IdempotencyKey.parse(key),
        new Cpf("52998224725"),
        ConvenioType.INSS,
        Money.of("1000.00"),
        12);
  }

  private LegacyHttpAdapter adapter(Duration timeout, CircuitBreaker breaker) {
    HttpClient http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    return new LegacyHttpAdapter(http, URI.create(stub.baseUrl()), "chave-teste", timeout, breaker);
  }

  private LegacyHttpAdapter adapter() {
    // Functional HTTP assertions are not latency benchmarks.
    return adapter(Duration.ofSeconds(10), breaker(20, 20, 50f));
  }

  private static CircuitBreaker breaker(int minimumCalls, int window, float failurePercent) {
    return CircuitBreaker.of(
        "test",
        LegacyHttpAdapter.breakerConfig(
            failurePercent, Duration.ofSeconds(2), minimumCalls, window, Duration.ofSeconds(30)));
  }

  @Test
  void should_confirm_on_200_sending_the_idempotency_key_and_the_api_key() {
    LegacyResult result = adapter().submit(submission(KEY));

    assertThat(result).isInstanceOf(LegacyResult.Confirmed.class);
    assertThat(((LegacyResult.Confirmed) result).averbacaoId()).isNotBlank();
    assertThat(stub.lastHeaders)
        .containsEntry("Idempotency-Key", KEY)
        .containsEntry("X-Api-Key", "chave-teste")
        .containsEntry("Method", "POST")
        .containsEntry("Path", "/v1/averbacoes");
    assertThat(stub.lastHeaders.get("Content-Type")).startsWith("application/json");
  }

  @Test
  void should_send_the_decrypted_data_as_json_numbers_and_strings() {
    adapter().submit(submission(KEY));

    assertThat(stub.lastBody)
        .contains("\"cpf\":\"52998224725\"")
        .contains("\"convenio\":\"INSS\"")
        .contains("\"amount\":1000.00")
        .contains("\"installments\":12");
  }

  @Test
  void should_treat_400_and_422_as_business_rejections() {
    stub.status = 400;
    assertThat(adapter().submit(submission(KEY))).isInstanceOf(LegacyResult.Rejected.class);
    stub.status = 422;
    assertThat(adapter().submit(submission(KEY))).isInstanceOf(LegacyResult.Rejected.class);
  }

  @Test
  void should_treat_capacity_server_and_auth_errors_as_transient() {
    for (int status : new int[] {503, 500, 502, 429, 401, 403, 404}) {
      stub.status = status;
      assertThat(adapter().submit(submission(KEY)))
          .as("status " + status)
          .isInstanceOf(LegacyResult.TransientFailure.class);
    }
  }

  @Test
  void should_treat_unreadable_200_responses_as_transient() {
    stub.body = "nao-e-json";
    assertThat(adapter().submit(submission(KEY))).isInstanceOf(LegacyResult.TransientFailure.class);
    stub.body = "{\"status\":\"AVERBADA\"}";
    assertThat(adapter().submit(submission(KEY))).isInstanceOf(LegacyResult.TransientFailure.class);
    stub.body = "{\"averbacaoId\":\"  \"}";
    assertThat(adapter().submit(submission(KEY))).isInstanceOf(LegacyResult.TransientFailure.class);
  }

  @Test
  void should_treat_a_refused_connection_as_transient() {
    stub.close();

    assertThat(adapter().submit(submission(KEY))).isInstanceOf(LegacyResult.TransientFailure.class);
    stub = StubLegacy.start();
  }

  @Test
  void should_time_out_slow_responses_and_report_transient() {
    var client = new br.com.consignado.platform.worker.support.TimeoutHttpClient();
    LegacyHttpAdapter slowAdapter =
        new LegacyHttpAdapter(
            client,
            URI.create(stub.baseUrl()),
            "test",
            Duration.ofMillis(300),
            breaker(20, 20, 50f));
    LegacyResult result = slowAdapter.submit(submission(KEY));
    assertThat(result).isInstanceOf(LegacyResult.TransientFailure.class);
    assertThat(client.lastRequest.timeout()).contains(Duration.ofMillis(300));
  }

  @Test
  void should_open_the_circuit_after_repeated_failures_and_stop_calling_the_legacy() {
    CircuitBreaker breaker = breaker(5, 5, 50f);
    LegacyHttpAdapter adapter = adapter(Duration.ofSeconds(2), breaker);
    stub.status = 503;

    for (int i = 0; i < 5; i++) {
      adapter.submit(submission(KEY));
    }
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    int callsWhenOpened = stub.requests.get();

    for (int i = 0; i < 10; i++) {
      assertThat(adapter.submit(submission(KEY))).isInstanceOf(LegacyResult.TransientFailure.class);
    }

    assertThat(stub.requests.get())
        .as("sem chamadas com o circuito aberto")
        .isEqualTo(callsWhenOpened);
  }

  @Test
  void should_not_count_business_rejections_as_circuit_failures() {
    CircuitBreaker breaker = breaker(5, 5, 50f);
    LegacyHttpAdapter adapter = adapter(Duration.ofSeconds(2), breaker);
    stub.status = 422;

    for (int i = 0; i < 20; i++) {
      adapter.submit(submission(KEY));
    }

    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
  }

  @Test
  void should_open_the_circuit_when_calls_are_slower_than_the_threshold() {
    CircuitBreaker breaker =
        CircuitBreaker.of(
            "slow",
            LegacyHttpAdapter.breakerConfig(
                50f, Duration.ofMillis(100), 4, 4, Duration.ofSeconds(30)));
    LegacyHttpAdapter adapter = adapter(Duration.ofSeconds(2), breaker);
    stub.delayMillis = 250;

    for (int i = 0; i < 4; i++) {
      adapter.submit(submission(KEY));
    }

    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
  }
}
