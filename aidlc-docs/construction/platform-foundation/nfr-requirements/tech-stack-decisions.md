# Tech Stack Decisions - U1 platform-foundation

Versoes verificadas no Maven Central em 2026-10-07 (releases estaveis). O parent fixa tudo no `dependencyManagement`; qualquer ajuste de patch e feito no Code Generation apos `mvn verify`.

## Plataforma

| Item | Decisao | Versao | Justificativa |
|---|---|---|---|
| Linguagem/Runtime | Java (OpenJDK) | **25 LTS** (local: 25.0.2) | Decisao do usuario; Virtual Threads, records |
| Build | Maven | 3.9.11 (local) | Presente no ambiente; reactor multi-modulo |
| Framework | Spring Boot | **4.1.1** | GA mais recente na verificacao (a linha 4.2 esta em milestone); suporte nativo a Java 25 |
| Web | Spring MVC + Virtual Threads | (Boot) | `spring.threads.virtual.enabled=true`; sem WebFlux (skill platform-practices) |
| Seguranca | Spring Security Resource Server (JWT/JWKS) | (Boot) | Validacao real de JWT (Q5-A dos requisitos) |
| Resiliencia | Resilience4j `resilience4j-spring-boot4` | **2.4.0** | Modulo dedicado a Boot 4 disponivel |
| API docs | springdoc-openapi-starter-webmvc-ui | **3.1.1** | Linha 3.x para Boot 4 (a validar no build) |
| AWS SDK | AWS SDK for Java v2 (BOM) | **2.55.12** | DynamoDB, SQS, KMS |
| Lambda | `aws-lambda-java-core` / `aws-lambda-java-events` | **1.4.0** / **3.16.1** | Handler do Dispatcher (runtime a decidir em U4) |
| Observabilidade | Micrometer + Prometheus registry, Micrometer Tracing + OTel bridge | (Boot) | Skills metrics/tracing |
| Logs | Logging estruturado nativo do Boot (ECS JSON) | (Boot) | Q5-A; sem dependencia extra |
| Persistencia relacional | Spring Data JPA + Hibernate, PostgreSQL driver, Flyway | (Boot) | FR-6.1 (usadas em U5) |

## Qualidade e Teste

| Item | Decisao | Versao | Observacao |
|---|---|---|---|
| Testes | JUnit 5 + AssertJ + Mockito | (Boot) | Skill testing-strategy |
| **PBT (PBT-09)** | **jqwik** | **1.10.1** | JUnit Platform; shrinking e seed (PBT-08); suporte stateful. Dependencia declarada no parent |
| Arquitetura | ArchUnit `archunit-junit5` | **1.5.1** | Regra de dependencia em U3 e U5 |
| Integracao | Testcontainers | **2.0.5** | PostgreSQL, DynamoDB Local, ElasticMQ e local-kms; perfil `integration`; requer Docker (pendente de instalacao) |
| Cobertura | JaCoCo Maven plugin | fixada no parent | Gates 80% / 50% |
| Formatacao | Spotless Maven plugin | **3.10.3** | google-java-format |
| Regras de build | maven-enforcer-plugin | fixada no parent | Java >= 25, sem versoes dinamicas |
| Vulnerabilidades | OWASP dependency-check-maven | **13.0.0** | Perfil `security` |
| SBOM | cyclonedx-maven-plugin | **2.9.3** | Gerado no `package` |

## Ambiente Local (Compose)

| Servico | Decisao | Tag |
|---|---|---|
| AWS local | DynamoDB Local + ElasticMQ + local-kms (LocalStack descartado: auth token obrigatorio desde 2026.03.0; ver Infrastructure Design) | A fixar no Code Generation (sem `latest`) |
| Relacional | PostgreSQL | A fixar (linha 17 ou 18 conforme registry) |
| Metricas | Prometheus | A fixar |
| Dashboards | Grafana | A fixar |
| Tracing | Jaeger (all-in-one com OTLP) | A fixar |
| Emissor JWT | Mock com endpoint JWKS (ex.: servidor leve de teste) | A decidir no Code Generation de U1/U3 |

As tags exatas serao consultadas nos registries no Code Generation; nao e possivel `docker pull` de verificacao hoje por ausencia de Docker.

## Alternativas Consideradas

| Decisao | Alternativa descartada | Motivo |
|---|---|---|
| Spring Boot 4.1.1 | 3.5.x | Linha mais antiga; menor tempo de suporte restante (SECURITY-09) |
| jqwik | junit-quickcheck | Menos mantida, sem stateful |
| Logging nativo ECS | Logback + logstash-encoder | Dependencia extra (SECURITY-10) |
| Versoes exatas + Enforcer | Lock file de terceiros | Maven nao possui lock nativo; Enforcer cobre a intencao da regra |

## Pendencias de Validacao (Code Generation)
1. `mvn verify` do parent com Spring Boot 4.1.1 + Resilience4j 2.4.0 + springdoc 3.1.1.
2. Confirmar compatibilidade do Testcontainers 2.0.5 e dos emuladores (DynamoDB Local, ElasticMQ, local-kms) com Docker instalado.
3. Fixar tags de imagem do Compose.
