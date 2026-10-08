package br.com.consignado.platform.starter;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Registra os defaults da plataforma com a menor precedencia: qualquer variavel de ambiente,
 * argumento ou arquivo de configuracao do servico sobrescreve (Twelve-Factor, NFR-U1-13 a 16).
 */
public class PlatformDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

  static final String PROPERTY_SOURCE_NAME = "platformDefaults";

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication app) {
    environment
        .getPropertySources()
        .addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults()));
  }

  static Map<String, Object> defaults() {
    Map<String, Object> map = new LinkedHashMap<>();

    // Logs estruturados (ECS/JSON) em stdout; correlacao via MDC (traceId/spanId).
    map.put("logging.structured.format.console", "ecs");
    map.put("logging.structured.ecs.service.name", "${spring.application.name:unknown}");

    // LGPD: mascara dados pessoais em todo valor de texto do JSON, inclusive stack traces.
    map.put(
        "logging.structured.json.customizer", PiiRedactingJsonMembersCustomizer.class.getName());

    // Actuator minimo: nenhum detalhe de saude exposto por padrao.
    map.put("management.endpoints.web.exposure.include", "health,info,metrics,prometheus");
    map.put("management.endpoint.health.probes.enabled", true);
    map.put("management.endpoint.health.show-details", "never");

    // Tags comuns de metricas (baixa cardinalidade).
    map.put("management.metrics.tags.service", "${spring.application.name:unknown}");
    map.put("management.metrics.tags.env", "${PLATFORM_ENV:local}");

    // Tracing: amostragem configuravel por ambiente; coletor OTLP opcional.
    map.put("management.tracing.sampling.probability", "${PLATFORM_TRACING_SAMPLING:1.0}");

    // Twelve-Factor: descartabilidade e concorrencia.
    map.put("server.shutdown", "graceful");
    map.put("spring.lifecycle.timeout-per-shutdown-phase", "30s");
    map.put("spring.threads.virtual.enabled", true);

    // Hardening: erros sem detalhes internos, sem cabecalho Server.
    map.put("server.server-header", "");
    map.put("server.error.include-message", "never");
    map.put("server.error.include-stacktrace", "never");
    map.put("server.error.include-binding-errors", "never");
    map.put("server.error.include-exception", false);
    return map;
  }
}
