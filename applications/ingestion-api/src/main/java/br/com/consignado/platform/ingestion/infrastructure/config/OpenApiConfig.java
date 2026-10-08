package br.com.consignado.platform.ingestion.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadados da documentacao OpenAPI da Ingestion API, com o esquema de autenticacao JWT. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

  static final String BEARER = "bearerAuth";

  @Bean
  OpenAPI ingestionOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Ingestion API - Credito Consignado")
                .version("1.0.0")
                .description(
                    "Borda de absorcao multi-tenant. Aceita a proposta com durabilidade e"
                        + " idempotencia (Idempotency-Key) e responde 202 sem esperar o legado."
                        + " Obtenha um token no ambiente local em GET /token?tenant=<id> do"
                        + " emissor local (porta 8082)."))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT com a claim `tenant` igual ao header X-Tenant-ID")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER));
  }
}
