package br.com.consignado.platform.worker.infrastructure.adapter.out.legacy;

import br.com.consignado.platform.shared.contract.HeaderNames;
import br.com.consignado.platform.worker.application.port.out.LegacyCorePort;
import br.com.consignado.platform.worker.application.port.out.LegacySubmission;
import br.com.consignado.platform.worker.domain.LegacyResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente HTTP do legado protegido por circuit breaker. Traduz tudo em {@link LegacyResult}: 200
 * confirma; 400/422 e recusa de negocio; qualquer outra coisa (503 de capacidade, 5xx, timeout,
 * rede, circuito aberto) e falha transitoria. A chave de idempotencia vai em todo envio, entao
 * repeticoes nunca duplicam a averbacao. Nao registra corpo, CPF nem valores.
 */
public final class LegacyHttpAdapter implements LegacyCorePort {

  private static final JsonMapper MAPPER =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private record LegacyRequest(String cpf, String convenio, BigDecimal amount, int installments) {}

  private record LegacyResponse(String averbacaoId, String status) {}

  /** Falha do legado que conta para o circuit breaker. */
  static final class LegacyUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    LegacyUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Configuracao do circuit breaker: abre com taxa de falhas ou de chamadas lentas acima do limite
   * (padrao do SDD: 5xx acima de 10% ou latencia acima de 2 s); apos o tempo de espera passa a
   * semi-aberto e testa poucas chamadas, evitando "retry storms".
   */
  public static CircuitBreakerConfig breakerConfig(
      float failureRatePercent,
      Duration slowCallThreshold,
      int minimumCalls,
      int windowSize,
      Duration openStateWait) {
    return CircuitBreakerConfig.custom()
        .failureRateThreshold(failureRatePercent)
        .slowCallRateThreshold(failureRatePercent)
        .slowCallDurationThreshold(slowCallThreshold)
        .minimumNumberOfCalls(minimumCalls)
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(windowSize)
        .waitDurationInOpenState(openStateWait)
        .permittedNumberOfCallsInHalfOpenState(5)
        .automaticTransitionFromOpenToHalfOpenEnabled(true)
        .recordException(LegacyUnavailableException.class::isInstance)
        .build();
  }

  private final HttpClient http;
  private final URI endpoint;
  private final String apiKey;
  private final Duration requestTimeout;
  private final CircuitBreaker breaker;

  public LegacyHttpAdapter(
      HttpClient http,
      URI baseUrl,
      String apiKey,
      Duration requestTimeout,
      CircuitBreaker breaker) {
    this.http = http;
    this.endpoint = baseUrl.resolve("/v1/averbacoes");
    this.apiKey = apiKey;
    this.requestTimeout = requestTimeout;
    this.breaker = breaker;
  }

  @Override
  public LegacyResult submit(LegacySubmission submission) {
    try {
      return breaker.executeSupplier(() -> call(submission));
    } catch (CallNotPermittedException | LegacyUnavailableException e) {
      return new LegacyResult.TransientFailure();
    }
  }

  private LegacyResult call(LegacySubmission submission) {
    try (var operation =
        br.com.consignado.platform.telemetry.Telemetry.start(
            "POST /v1/averbacoes", io.opentelemetry.api.trace.SpanKind.CLIENT)) {
      operation.span().setAttribute("http.request.method", "POST");
      try {
        return callTraced(submission);
      } catch (RuntimeException e) {
        operation.failed(e);
        throw e;
      }
    }
  }

  private LegacyResult callTraced(LegacySubmission submission) {
    HttpResponse<String> response;
    try {
      response = http.send(request(submission), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new LegacyUnavailableException("Legado inacessivel", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LegacyUnavailableException("Chamada ao legado interrompida", e);
    }
    int status = response.statusCode();
    io.opentelemetry.api.trace.Span.current().setAttribute("http.response.status_code", status);
    if (status == 200) {
      return confirmed(response.body());
    }
    if (status == 400 || status == 422) {
      return new LegacyResult.Rejected("legacy-status-" + status);
    }
    throw new LegacyUnavailableException("Legado respondeu " + status, null);
  }

  private HttpRequest request(LegacySubmission submission) {
    String body =
        MAPPER.writeValueAsString(
            new LegacyRequest(
                submission.cpf().digits(),
                submission.convenio().name(),
                submission.amount().amount(),
                submission.installments()));
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(endpoint)
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .header(HeaderNames.IDEMPOTENCY_KEY, submission.idempotencyKey().toString())
            .header("X-Api-Key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body));
    br.com.consignado.platform.telemetry.TraceContext.capture().forEach(builder::header);
    return builder.build();
  }

  private static LegacyResult confirmed(String body) {
    try {
      LegacyResponse parsed = MAPPER.readValue(body, LegacyResponse.class);
      if (parsed.averbacaoId() == null || parsed.averbacaoId().isBlank()) {
        throw new LegacyUnavailableException("Resposta do legado sem identificador", null);
      }
      return new LegacyResult.Confirmed(parsed.averbacaoId());
    } catch (RuntimeException e) {
      if (e instanceof LegacyUnavailableException unavailable) {
        throw unavailable;
      }
      throw new LegacyUnavailableException("Resposta do legado ilegivel", e);
    }
  }
}
