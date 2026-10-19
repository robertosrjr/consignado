package br.com.consignado.platform.legacy.web;

import br.com.consignado.platform.legacy.capacity.CapacityGate;
import br.com.consignado.platform.legacy.config.LegacyFaultPolicy;
import br.com.consignado.platform.legacy.config.LegacyMetrics;
import br.com.consignado.platform.legacy.ledger.AverbacaoLedger;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoint de averbacao do legado simulado. Aplica o teto de capacidade (503 acima do limite),
 * falhas simuladas e idempotencia por {@code Idempotency-Key}.
 */
@RestController
@RequestMapping("/v1/averbacoes")
@Tag(name = "Averbacoes", description = "Averbacao de propostas no Core Banking simulado")
class AverbacaoController {

  private static final Pattern UUID_FORMAT =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

  private final CapacityGate gate;
  private final AverbacaoLedger ledger;
  private final LegacyFaultPolicy faultPolicy;
  private final LegacyMetrics metrics;

  AverbacaoController(
      CapacityGate gate,
      AverbacaoLedger ledger,
      LegacyFaultPolicy faultPolicy,
      LegacyMetrics metrics) {
    this.gate = gate;
    this.ledger = ledger;
    this.faultPolicy = faultPolicy;
    this.metrics = metrics;
  }

  @PostMapping
  @Operation(
      summary = "Averba uma proposta",
      description =
          "Idempotente por Idempotency-Key: repeticoes devolvem o mesmo resultado (X-Replayed)."
              + " Acima da capacidade (janela de 1 s) responde 503 com Retry-After.")
  @ApiResponse(responseCode = "200", description = "Averbacao registrada ou repetida")
  @ApiResponse(responseCode = "400", description = "Payload ou Idempotency-Key invalidos")
  @ApiResponse(responseCode = "401", description = "X-Api-Key ausente ou invalida")
  @ApiResponse(responseCode = "503", description = "Capacidade excedida ou falha simulada")
  ResponseEntity<Object> averbar(
      @RequestHeader("Idempotency-Key") String rawKey,
      @Valid @RequestBody AverbacaoRequest request) {
    Timer.Sample sample = Timer.start();
    try {
      UUID key = parseKey(rawKey);
      if (!gate.tryEnter()) {
        metrics.rejectedCapacity();
        return unavailable("Capacidade do legado excedida");
      }
      if (simulateFailure()) {
        metrics.failedChaos();
        return unavailable("Falha simulada do legado");
      }
      AverbacaoLedger.Registration registration = ledger.register(key);
      metrics.averbacao(registration.duplicate());
      metrics.accepted();
      ResponseEntity.BodyBuilder ok = ResponseEntity.ok();
      if (registration.duplicate()) {
        ok.header("X-Replayed", "true");
      }
      return ok.body(new AverbacaoResponse(registration.averbacaoId().toString(), "AVERBADA"));
    } finally {
      sample.stop(metrics.latency());
    }
  }

  private static UUID parseKey(String rawKey) {
    if (rawKey == null || !UUID_FORMAT.matcher(rawKey).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key invalida");
    }
    return UUID.fromString(rawKey);
  }

  /** Aplica latencia e taxa de erro configuradas; retorna {@code true} se a chamada deve falhar. */
  private boolean simulateFailure() {
    LegacyFaultPolicy.State chaos = faultPolicy.state();
    if (chaos.latencyMs() > 0) {
      try {
        Thread.sleep(chaos.latencyMs());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return true;
      }
    }
    return chaos.errorRate() > 0 && ThreadLocalRandom.current().nextDouble() < chaos.errorRate();
  }

  private static ResponseEntity<Object> unavailable(String detail) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detail);
    body.setTitle("Service Unavailable");
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, "1")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(body);
  }
}
