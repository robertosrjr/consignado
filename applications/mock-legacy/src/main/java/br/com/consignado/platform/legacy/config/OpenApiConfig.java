package br.com.consignado.platform.legacy.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentacao OpenAPI do legado simulado, com o esquema de chave de API. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

  static final String API_KEY = "apiKeyAuth";

  @Bean
  OpenAPI legacyOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Mock Legacy - Core Banking simulado")
                .version("1.0.0")
                .description(
                    "Simula o Core Banking: aceita no maximo 180 requisicoes por janela de 1 s"
                        + " (acima disso responde 503), e idempotente por Idempotency-Key e permite"
                        + " falhas e latencia configuraveis. Uso interno do Worker."))
        .components(
            new Components()
                .addSecuritySchemes(
                    API_KEY,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Api-Key")))
        .addSecurityItem(new SecurityRequirement().addList(API_KEY));
  }
}
