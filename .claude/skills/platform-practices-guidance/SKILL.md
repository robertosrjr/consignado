---
name: platform-practices-guidance
description: "Use this skill when designing applications for cloud/platform deployment, applying Twelve-Factor App principles, or choosing between reactive vs. synchronous architectures."
---

# Práticas de Plataforma: Twelve-Factor & Reactive Manifesto

Você é um especialista em **Twelve-Factor App**, **Reactive Manifesto**, **design para cloud** e **padrões operacionais**. Sua missão é orientar o design de aplicações robustas, escaláveis e resilientes.

## Quando Usar Esta Skill

- Estruturar aplicação para cloud/Kubernetes
- Aplicar Twelve-Factor principles
- Decidir entre WebFlux (reativo) vs. MVC (síncrono)
- Configurar healthchecks, readiness, liveness
- Otimizar para descartabilidade (stateless)
- Implementar graceful shutdown
- Gerenciar configuração e variáveis de ambiente

## The Twelve-Factor App

Referência completa: [12factor.net/pt_br](https://12factor.net/pt_br/)

| # | Fator | Aplicação Prática |
|---|-------|-------------------|
| 1 | **Codebase** | Um repo versionado por app; múltiplos deploys (dev, staging, prod) |
| 2 | **Dependências** | Declaradas explicitamente (Maven/Gradle), nunca assumidas |
| 3 | **Config** | Via variáveis de ambiente/`application.yml` externalizado |
| 4 | **Backing Services** | Bancos, filas, caches como recursos anexáveis (DB_URL=...) |
| 5 | **Build, Release, Run** | Fases estritamente separadas no CI/CD |
| 6 | **Processos** | Stateless; estado fica em backing services |
| 7 | **Port Binding** | Serviço exposto via porta (`server.port`), autocontido |
| 8 | **Concorrência** | Escalar via múltiplas instâncias, não threads gigantes |
| 9 | **Descartabilidade** | Startup rápido + graceful shutdown (`SmartLifecycle`/`@PreDestroy`) |
| 10 | **Paridade Dev/Prod** | Ambientes o mais parecidos (Docker/Testcontainers) |
| 11 | **Logs** | Stream de eventos (stdout), nunca gerenciar arquivo local |
| 12 | **Admin Processes** | Migrações/scripts como processos isolados (Flyway/Liquibase) |

### Implementação em Spring Boot

```yaml
# application.yml — externalize tudo
spring:
  datasource:
    url: ${DB_URL:jdbc:h2:mem:testdb}
    username: ${DB_USER:sa}
    password: ${DB_PASSWORD:}
  
  jpa:
    hibernate:
      ddl-auto: ${JPA_DDL:validate}

server:
  port: ${SERVER_PORT:8080}
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

## Reactive Manifesto

Referência: [reactivemanifesto.org](https://www.reactivemanifesto.org/)

### 4 Princípios: **Responsivo, Resiliente, Elástico, Message-Driven**

#### Quando usar WebFlux (Reativo)

✅ **Use se:**
- Gargalo é **I/O** (muitas chamadas a APIs, streaming, alta concorrência)
- Precisa lidar com **milhares de conexões** com poucos recursos
- Integrações com múltiplos backends síncronos

❌ **Evite se:**
- Maioria são CRUDs convencionais (use MVC + Virtual Threads)
- Time não tem experiência com Reactor/Mono/Flux
- Debugging é crítico (reativo é mais complexo)

**Não misture** os dois paradigmas (MVC bloqueante e WebFlux) na mesma cadeia de chamadas.

### Arquitetura Reativa em Spring

```java
@RestController
@RequestMapping("/api/orders")
public class ReactiveOrderController {
    
    private final OrderService service;
    
    @GetMapping
    public Flux<OrderResponse> list() {
        return service.findAll()
            .map(OrderResponse::from);
    }
    
    @GetMapping("/{id}")
    public Mono<OrderResponse> findById(@PathVariable Long id) {
        return service.findById(id)
            .map(OrderResponse::from)
            .switchIfEmpty(Mono.error(new NotFoundException()));
    }
    
    @PostMapping
    public Mono<OrderResponse> create(@RequestBody Mono<CreateOrderRequest> request) {
        return request
            .flatMap(req -> service.create(req.toDomain()))
            .map(OrderResponse::from);
    }
}
```

### Regras se optar por WebFlux

| Regra | Por quê |
|-------|---------|
| **Nunca `.block()` em produção** | Bloqueia thread reactor |
| **Use R2DBC em vez de JDBC** | Acesso a dados não bloqueante |
| **Respeite backpressure** | Não afogar consumidor |
| **Propague contexto via `Context`** | Não use `ThreadLocal` (MDC) |
| **Use `webclient` em vez de `RestTemplate`** | RestTemplate é bloqueante |

### Exemplo: Propagação de Contexto
```java
return service.process()
    .contextWrite(Context.of(
        "traceId", UUID.randomUUID().toString(),
        "userId", getCurrentUserId()
    ))
    .subscriberContext()  // Acessar contexto
    .map(value -> {
        String traceId = (String) ReactorContext.get("traceId");
        // usar traceId
    });
```

## Healthchecks & Operacionalidade

### Actuator do Spring (Liveness/Readiness)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
  
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when-authorized
  
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

**Kubernetes probes:**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 5
  periodSeconds: 5
```

## Graceful Shutdown

```java
@Configuration
public class ShutdownConfig {
    
    @Bean
    public SmartLifecycle gracefulShutdown(ApplicationContext context) {
        return new SmartLifecycle() {
            @Override
            public void stop(Runnable callback) {
                // Finalizar trabalhos pendentes
                // Drains de queues, conexões
                callback.run();  // Sinalizar conclusão
            }
        };
    }
}
```

## Como Responder

1. **Análise**: Avaliar arquitetura atual
2. **Diagnóstico**: Quais 12FA estão faltando?
3. **Recomendação**: Síncrono vs. reativo?
4. **Implementação**: Código e configuração prontos
