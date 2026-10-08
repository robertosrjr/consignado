package br.com.consignado.platform.ingestion.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import br.com.consignado.platform.ingestion.IngestionApplication;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Sem JWKS ou sem chave de payload o servico nao sobe (nada de valores padrao inseguros). */
class RequiredConfigurationTest {

  private static Throwable start(String... properties) {
    return catchThrowable(
        () ->
            new SpringApplicationBuilder(IngestionApplication.class)
                .web(WebApplicationType.NONE)
                .properties(properties)
                .properties("ingestion.inbox.type=memory", "server.port=0")
                .run());
  }

  @Test
  void should_fail_startup_without_the_jwks_uri() {
    assertThat(
            start(
                "ingestion.jwt.jwk-set-uri=",
                "ingestion.payload.local-key=" + Base64.getEncoder().encodeToString(new byte[32])))
        .isNotNull();
  }

  @Test
  void should_fail_startup_in_local_mode_without_the_payload_key() {
    assertThat(
            start(
                "ingestion.jwt.jwk-set-uri=http://localhost:9/jwks",
                "ingestion.payload.local-key="))
        .isNotNull();
  }
}
