package br.com.consignado.platform.ingestion.infrastructure.adapter.in.web;

import br.com.consignado.platform.ingestion.domain.InboxUnavailableException;
import br.com.consignado.platform.ingestion.domain.TenantQuotaExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Mapeia excecoes da ingestao para Problem Details (RFC 7807) sem vazar detalhes. Precede o handler
 * generico da plataforma (que responde 500 para o restante).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class IngestionExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(IngestionExceptionHandler.class);

  @ExceptionHandler(InvalidRequestException.class)
  ResponseEntity<ProblemDetail> invalid(InvalidRequestException ex) {
    return problem(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), null);
  }

  @ExceptionHandler(TenantForbiddenException.class)
  ResponseEntity<ProblemDetail> forbidden(TenantForbiddenException ex) {
    return problem(HttpStatus.FORBIDDEN, "Forbidden", "Tenant nao autorizado", null);
  }

  @ExceptionHandler(TenantQuotaExceededException.class)
  ResponseEntity<ProblemDetail> quota(TenantQuotaExceededException ex) {
    return problem(
        HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", "Cota do parceiro excedida", "1");
  }

  @ExceptionHandler(InboxUnavailableException.class)
  ResponseEntity<ProblemDetail> unavailable(InboxUnavailableException ex) {
    // Sem a mensagem da excecao: o SDK pode incluir a chave ou o item da requisicao (LGPD art. 6o
    // III). O tipo da causa raiz basta para distinguir indisponibilidade de erro de configuracao.
    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
    LOG.atError()
        .addKeyValue("event", "inbox_unavailable")
        .addKeyValue("root_cause", cause.getClass().getName())
        .log("inbox_unavailable");
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Service Unavailable",
        "Servico temporariamente indisponivel; repita a chamada",
        "1");
  }

  private static ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String title, String detail, String retryAfter) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
    body.setTitle(title);
    ResponseEntity.BodyBuilder builder =
        ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store");
    if (retryAfter != null) {
      builder.header(HttpHeaders.RETRY_AFTER, retryAfter);
    }
    return builder.body(body);
  }
}
