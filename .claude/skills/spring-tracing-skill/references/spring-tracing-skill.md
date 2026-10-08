# Spring Boot 4.1.1: tracing distribuído (Micrometer Tracing & OTel)

Padrões de implementação, configurações práticas e código de referência para o desenvolvimento e na revisão de recursos de rastreamento distribuído em Java 21 e Spring Boot 4.1.1.

## 1. Configurações de Tracing Operacionais (application.properties)

No Spring Boot 4.1.1, você deve configurar o Actuator para habilitar o rastreamento, ajustar a probabilidade de amostragem e definir os padrões de correlação com o logger.

```properties
# Ajuste da amostragem (Default: 0.1 / 10%). Definimos como 1.0 (100%) apenas para desenvolvimento/testes
management.tracing.sampling.probability=1.0

# Vinculação automática de TraceID/SpanID aos logs estruturados (MDC)
# Opcional: Define um formato customizado para correlação de logs de tracing
logging.pattern.correlation=[${spring.application.name:},%X{traceId:-},%X{spanId:-}]
logging.include-application-name=false
```

Para enviar os traces utilizando o padrão de mercado do **OpenTelemetry Protocol (OTLP)** via rede, garanta o uso das propriedades:
```properties
management.opentelemetry.tracing.export.otlp.url=http://otlp-collector.internal:4318/v1/traces
```

---

## 2. Padrões de Código para Propagação de Contexto

### Regra Crucial: Nunca instancie clientes HTTP diretamente
Se você criar instâncias de `RestTemplate`, `RestClient` ou `WebClient` usando `new`, a propagação automática de cabeçalhos de tracing distribuídos (como W3C Trace Context) **não funcionará**. 

Sempre injete e utilize os **Builders gerenciados pelo Spring**:

```java
import org.springframework.web.client.RestClient;
import org.springframework.stereotype.Service;

@Service
public class IntegracaoClienteService {

    private final RestClient restClient;

    // O Spring injeta automaticamente o builder configurado com o interceptor de Tracing
    public IntegracaoClienteService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                            .baseUrl("https://api.gatewayexterno.com")
                            .build();
    }

    public String obterDadosExternos(String endpoint) {
        return this.restClient.get()
                              .uri(endpoint)
                              .retrieve()
                              .body(String.class);
    }
}
```

---

## 3. Criação de Spans Customizados (Observation API)

A nova prática recomendada para Spring Boot 4.1.1 é o uso da **Observation API** (Micrometer Observation). Um único código de observação cria de forma transparente tanto uma métrica (Prometheus) quanto um Span de Tracing correspondente:

```java
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

@Component
public class ProcessamentoComplexo {

    private final ObservationRegistry observationRegistry;

    public ProcessamentoComplexo(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    public void executarOperacaoPesada() {
        // Criação de uma observação que resultará em um Span no OpenTelemetry
        Observation.createNotStarted("processamento-pesado", this.observationRegistry)
                   .lowCardinalityKeyValue("operacao.tipo", "batch-calculo")
                   .observe(() -> {
                       // Sua lógica de negócios pesada aqui...
                       simularProcessamento();
                   });
    }

    private void simularProcessamento() {
        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
```

---

## 4. Uso de Baggage para Propagação de Metadados

Se você precisa passar uma informação técnica de negócio de ponta a ponta (como o ID de uma transação ou de um lojista parceiro) em todas as requisições HTTP internas sem alterar os parâmetros de API, utilize a funcionalidade de **Baggage**:

```java
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

@Component
public class ControleFluxoService {

    private final Tracer tracer;

    public ControleFluxoService(Tracer tracer) {
        this.tracer = tracer;
    }

    public void processarFluxoComBaggage() {
        // Cria e define a baggage no escopo
        try (BaggageInScope scope = this.tracer.createBaggageInScope("parceiro_id", "parceiro-123")) {
            // Toda requisição HTTP disparada neste escopo propagará o cabeçalho 'parceiro_id: parceiro-123'
            executarFluxoDeNegocio();
        }
    }

    private void executarFluxoDeNegocio() {
        // Lógica de negócio...
    }
}
```

Para propagar a Baggage automaticamente como campo correlacionado no log MDC da aplicação atual, declare:
```properties
management.tracing.baggage.correlation.fields=parceiro_id
```
