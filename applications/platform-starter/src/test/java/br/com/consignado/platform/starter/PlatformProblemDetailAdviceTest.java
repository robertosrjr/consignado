package br.com.consignado.platform.starter;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class PlatformProblemDetailAdviceTest {

  private final PlatformProblemDetailAdvice advice = new PlatformProblemDetailAdvice();

  @Test
  void should_return_generic_500_without_leaking_the_exception_message() {
    ResponseEntity<Object> response =
        advice.handleUnexpected(
            new IllegalStateException("SELECT * FROM proposal WHERE cpf = 52998224725"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    ProblemDetail body = (ProblemDetail) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getDetail()).isEqualTo(PlatformProblemDetailAdvice.GENERIC_DETAIL);
    assertThat(String.valueOf(body)).doesNotContain("SELECT").doesNotContain("52998224725");
    assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
  }

  @Test
  void should_not_log_the_exception_message_nor_the_cause() {
    Logger logger = (Logger) LoggerFactory.getLogger(PlatformProblemDetailAdvice.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      advice.handleUnexpected(
          new IllegalStateException("SELECT * FROM proposal WHERE cpf = 52998224725"));
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.get(0);
    assertThat(event.getFormattedMessage()).isEqualTo("unexpected_error");
    assertThat(event.getThrowableProxy()).as("stack trace carrega mensagens").isNull();
    assertThat(String.valueOf(event.getKeyValuePairs()))
        .doesNotContain("52998224725")
        .doesNotContain("SELECT")
        .contains("java.lang.IllegalStateException");
  }

  @Test
  void should_keep_spring_mvc_status_codes_for_standard_exceptions() throws Exception {
    HttpMessageNotReadableException ex =
        new HttpMessageNotReadableException(
            "corpo invalido", new MockHttpInputMessage(new byte[0]));

    ResponseEntity<Object> response =
        advice.handleException(ex, new ServletWebRequest(new MockHttpServletRequest()));

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
  }
}
