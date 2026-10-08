package br.com.consignado.platform.legacy.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.legacy.LegacyApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Sem LEGACY_API_KEY o servico nao sobe (nenhuma credencial padrao, SECURITY-09). */
class ApiKeyRequiredTest {

  @Test
  void should_fail_startup_when_the_api_key_is_missing() {
    Throwable failure =
        org.assertj.core.api.Assertions.catchThrowable(
            () ->
                new SpringApplicationBuilder(LegacyApplication.class)
                    .web(WebApplicationType.NONE)
                    .properties("legacy.api-key=", "server.port=0", "management.server.port=-1")
                    .run());

    assertThat(failure).isNotNull();
  }
}
