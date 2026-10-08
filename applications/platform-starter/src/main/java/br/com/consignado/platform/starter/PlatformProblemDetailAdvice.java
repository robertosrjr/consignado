package br.com.consignado.platform.starter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Tratamento global de excecoes (RFC 7807). Falha fechada: excecao nao mapeada vira 500 generico,
 * sem stack trace, SQL ou nomes internos; o detalhe vai somente ao log estruturado. Excecoes padrao
 * do Spring MVC mantem seus codigos (400, 405, 415, ...).
 */
@RestControllerAdvice
public class PlatformProblemDetailAdvice extends ResponseEntityExceptionHandler {

  static final String GENERIC_DETAIL = "Erro interno. Tente novamente mais tarde.";

  private static final Logger LOG = LoggerFactory.getLogger(PlatformProblemDetailAdvice.class);

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleUnexpected(Exception ex) {
    // Minimizacao (LGPD art. 6o III): registra os tipos, nunca a mensagem da excecao, que pode
    // carregar CPF ou um trecho de consulta com dado do cliente. A stack trace fica fora do log
    // justamente por conter mensagens; o tipo e a origem bastam para o diagnostico.
    Throwable root = rootCause(ex);
    LOG.atError()
        .addKeyValue("event", "unexpected_error")
        .addKeyValue("exception", ex.getClass().getName())
        .addKeyValue("root_cause", root.getClass().getName())
        .addKeyValue("origin", origin(root))
        .log("unexpected_error");
    ProblemDetail body =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_DETAIL);
    body.setTitle("Internal Server Error");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(body);
  }

  private static Throwable rootCause(Throwable ex) {
    Throwable current = ex;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current;
  }

  /** Primeiro quadro da pilha da causa raiz ({@code classe.metodo:linha}), sem mensagens. */
  private static String origin(Throwable root) {
    StackTraceElement[] frames = root.getStackTrace();
    if (frames.length == 0) {
      return "unknown";
    }
    StackTraceElement frame = frames[0];
    return frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber();
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    HttpHeaders noStore = new HttpHeaders();
    noStore.putAll(headers);
    noStore.setCacheControl("no-store");
    return super.handleExceptionInternal(ex, body, noStore, statusCode, request);
  }
}
