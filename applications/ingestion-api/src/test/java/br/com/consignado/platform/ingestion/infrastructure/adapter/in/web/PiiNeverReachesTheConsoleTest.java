package br.com.consignado.platform.ingestion.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Prova a rede de protecao da telemetria no servico real (LGPD art. 6o III): com os defaults da
 * plataforma, nenhum CPF chega ao stdout, mesmo vindo da mensagem de uma excecao de biblioteca.
 */
@SpringBootTest(
    properties = {
      "ingestion.inbox.type=memory",
      "ingestion.jwt.jwk-set-uri=http://localhost:9/jwks",
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false"
    })
@ExtendWith(OutputCaptureExtension.class)
class PiiNeverReachesTheConsoleTest {

  private static final String CPF = "52998224725";
  private static final Logger LOG = LoggerFactory.getLogger(PiiNeverReachesTheConsoleTest.class);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add(
        "ingestion.payload.local-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  @Test
  void should_mask_a_cpf_that_arrives_through_a_log_field(CapturedOutput output) {
    LOG.atError()
        .addKeyValue("event", "unexpected_error")
        .addKeyValue("detail", "SELECT * FROM proposal WHERE cpf = " + CPF)
        .log("unexpected_error");

    assertThat(output.getOut()).contains("unexpected_error");
    assertThat(output.getOut()).doesNotContain(CPF);
    assertThat(output.getOut()).contains("***.***.247-25");
  }

  @Test
  void should_mask_a_cpf_that_arrives_through_the_message(CapturedOutput output) {
    LOG.error("falha ao tratar a proposta do cpf {}", CPF);

    assertThat(output.getOut()).doesNotContain(CPF);
    assertThat(output.getOut()).contains("***.***.247-25");
  }
}
