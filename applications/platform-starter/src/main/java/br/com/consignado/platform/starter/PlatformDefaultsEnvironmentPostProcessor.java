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
    Map<String, Object> settings = defaults();
    String endpoint = environment.getProperty("PLATFORM_OTLP_TRACES_ENDPOINT");
    if (endpoint != null && !endpoint.isBlank()) {
      settings.put("management.opentelemetry.tracing.export.otlp.endpoint", endpoint);
    }
    environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, settings));
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
    // Pushgateway merges metric families across jobs: HTTP timers must use the same type.
    map.put("management.metrics.distribution.percentiles-histogram.http.server.requests", true);

    // Pushgateway: envio ativo para quando o Prometheus nao alcanca o servico (ex.: carga local
    // contra o Prometheus do EC2). Falha fechada: so liga com PLATFORM_PUSHGATEWAY_ENABLED=true.
    // Um grupo por servico e instancia; PUT no desligamento guarda a ultima leitura do teste.
    String pushgateway = "management.prometheus.metrics.export.pushgateway.";
    map.put(pushgateway + "enabled", "${PLATFORM_PUSHGATEWAY_ENABLED:false}");
    map.put(pushgateway + "address", "${PLATFORM_PUSHGATEWAY_ADDRESS:localhost:9091}");
    map.put(pushgateway + "job", "${spring.application.name:unknown}");
    map.put(pushgateway + "grouping-key.instance", "${PLATFORM_INSTANCE_ID:local}");
    map.put(pushgateway + "push-rate", "${PLATFORM_PUSHGATEWAY_PUSH_RATE:10s}");
    map.put(pushgateway + "shutdown-operation", "${PLATFORM_PUSHGATEWAY_SHUTDOWN:put}");

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
