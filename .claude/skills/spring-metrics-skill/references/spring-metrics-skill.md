# Spring Boot 4.1.1: métricas com Micrometer (Java 21)

Padrões, diretrizes e código de referência para o registro, customização e otimização de métricas do sistema utilizando Micrometer e Spring Boot Actuator 4.1.1.

## 1. Habilitação de Métricas JVM (Java 21 Virtual Threads)

O Spring Boot 4.1.1 e o Micrometer oferecem suporte nativo a métricas detalhadas da JVM rodando em Java 21. Para capturar estatísticas sobre as novas threads virtuais da JVM, a dependência `io.micrometer:micrometer-java21` deve estar presente no classpath (ex: `pom.xml` ou `build.gradle`).

As métricas geradas incluem:
- `jvm.threads.virtual` (estatísticas de threads virtuais)
- `jvm.memory.used`, `jvm.gc.memory.allocated`, etc.

---

## 2. Injeção de MeterRegistry e Métricas Customizadas

### A. Registro de Métricas via Injeção Direta do MeterRegistry
Para registrar métricas customizadas, injete o `MeterRegistry` gerenciado pelo Spring no seu componente e inicialize o medidor.

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.springframework.stereotype.Component;

@Component
public class PedidoProcessador {

    private final Counter pedidoSucessoCounter;
    private final Counter pedidoFalhaCounter;

    public PedidoProcessador(MeterRegistry registry) {
        // Inicialização recomendada no construtor
        this.pedidoSucessoCounter = registry.counter("pedidos.processados", "status", "sucesso");
        this.pedidoFalhaCounter = registry.counter("pedidos.processados", "status", "falha");
    }

    public void processar(boolean sucesso) {
        if (sucesso) {
            pedidoSucessoCounter.increment();
        } else {
            pedidoFalhaCounter.increment();
        }
    }
}
```

### B. Registro de Métricas complexas usando MeterBinder
Se a sua métrica depende de outro Bean que pode não estar totalmente pronto na inicialização rápida do Spring, utilize um `MeterBinder`. Essa é a prática recomendada pelas documentações oficiais para garantir os relacionamentos corretos de dependência:

```java
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Queue;

@Configuration(proxyBeanMethods = false)
public class FilaMetricsConfiguration {

    @Bean
    public MeterBinder filaTamanhoMetrics(Queue<String> filaDeProcessamento) {
        // Registra o tamanho de uma fila como um Gauge monitorado automaticamente
        return (registry) -> Gauge.builder("fila.processamento.tamanho", filaDeProcessamento::size)
                                  .description("Quantidade de mensagens aguardando processamento na fila")
                                  .register(registry);
    }
}
```

---

## 3. Gestão de Cardinalidade e Filtros de Métricas (MeterFilter)

**O Problema da Alta Cardinalidade:** Adicionar identificadores dinâmicos (como `user_id`, `cpf`, `email`, `timestamp`) como chaves/tags em suas métricas gera uma explosão de séries temporais na ferramenta de monitoramento (ex: Prometheus), degradando severamente a performance operacional.

### Uso do MeterFilter para Higienização e Renomeação de Tags
Com o Spring Boot 4.1.1, você pode interceptar a criação de medidores e filtrar ou renomear tags utilizando a interface `MeterFilter`:

```java
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MetricsSanitizerConfiguration {

    @Bean
    public MeterFilter renomearEFiltrarTags() {
        // Renomeia a tag 'region' para 'area' apenas para métricas sob o prefixo 'com.minhaempresa'
        return MeterFilter.renameTag("com.minhaempresa", "mytag.region", "mytag.area");
    }

    @Bean
    public MeterFilter desabilitarMetricasDeTeste() {
        // Desabilita totalmente métricas temporárias ou de terceiros que geram ruído
        return MeterFilter.denyNameStartsWith("example.remote");
    }
}
```

---

## 4. Configurações de Escopo Global (application.properties)

Aplique tags comuns a todos os seus medidores para drill-down no Prometheus sem precisar escrevê-las no código:

```properties
# Adiciona as tags globais 'region' e 'environment' a todas as métricas publicadas
management.metrics.tags.region=us-east-1
management.metrics.tags.environment=production

# Desabilita o endpoint in-memory "simple" para não consumir memória desnecessária se Prometheus estiver ativo
management.simple.metrics.export.enabled=false
```
