package br.com.consignado.platform.legacy.web;

import br.com.consignado.platform.legacy.config.LegacyFaultPolicy;
import br.com.consignado.platform.legacy.config.LegacyProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Controle exclusivo de experimentos locais, separado da API de negocio. */
@RestController
@Profile("sre-experiment")
@ConditionalOnProperty(name = "legacy.fault-control.enabled", havingValue = "true")
@RequestMapping("/internal/sre/fault")
class FaultControlController {

  private final LegacyFaultPolicy policy;
  private final byte[] expectedToken;

  FaultControlController(
      LegacyFaultPolicy policy,
      LegacyProperties properties,
      @Value("${legacy.fault-control.token:}") String token) {
    if (token.isBlank() || token.length() < 32) {
      throw new IllegalArgumentException(
          "Controle SRE exige token proprio de ao menos 32 caracteres");
    }
    if (properties.chaos().latencyMs() != 0 || properties.chaos().errorRate() != 0) {
      throw new IllegalArgumentException("Controle SRE exige baseline de caos neutra");
    }
    this.policy = policy;
    this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
  }

  @GetMapping
  ResponseEntity<LegacyFaultPolicy.State> state(HttpServletRequest request) {
    authorize(request);
    return response(policy.state());
  }

  @PostMapping
  ResponseEntity<LegacyFaultPolicy.State> activate(
      HttpServletRequest request, @Valid @RequestBody FaultRequest fault) {
    authorize(request);
    try {
      return response(policy.activate(fault.latencyMs(), fault.errorRate(), fault.durationMs()));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parametros de falha invalidos");
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Ja existe uma falha ativa");
    }
  }

  @DeleteMapping
  ResponseEntity<LegacyFaultPolicy.State> restore(HttpServletRequest request) {
    authorize(request);
    return response(policy.restore());
  }

  private void authorize(HttpServletRequest request) {
    String address = request.getRemoteAddr();
    if (!("127.0.0.1".equals(address)
        || "::1".equals(address)
        || "0:0:0:0:0:0:0:1".equals(address))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Controle restrito a loopback");
    }
    String token = request.getHeader("X-SRE-Control-Token");
    if (token == null
        || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), expectedToken)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial de controle invalida");
    }
  }

  private static ResponseEntity<LegacyFaultPolicy.State> response(LegacyFaultPolicy.State state) {
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(state);
  }

  record FaultRequest(
      @NotNull Long latencyMs, @NotNull Double errorRate, @NotNull Long durationMs) {}
}
