# Units of Work

Decomposicao alinhada ao Application Design e ao `execution-plan.md`. Terminologia: **Service** = implantavel de forma independente (`ingestion-api`, `dispatcher-lambda`, `worker`, `mock-legacy`); **Module** = agrupamento logico dentro do build Maven; **Unit of Work** = contexto de planejamento e entrega. Cada unidade conclui design e codigo antes da proxima.

## Visao Geral

| # | Unidade | Tipo | Estilo | Estagios de Construction |
|---|---|---|---|---|
| U1 | `platform-foundation` | Build + modulo `shared` + ambiente local | Biblioteca / tooling | NFR Req, NFR Design, Infra Design, Code |
| U2 | `mock-legacy` | Service | Simples | Functional, Code |
| U3 | `ingestion-api` | Service | Hexagonal | Functional, NFR Req, NFR Design, Infra Design, Code |
| U4 | `dispatcher-lambda` | Service (Lambda) | Simples | Functional, NFR Req, NFR Design, Infra Design, Code |
| U5 | `worker` | Service | Hexagonal | Functional, NFR Req, NFR Design, Infra Design, Code |
| U6 | `infrastructure-terraform` | IaC | Modulos Terraform | Infra Design, Code |
| U7 | `load-tests-and-dashboards` | Testes e dashboards | k6 + Grafana | Functional, Code |

## U1. platform-foundation

**Proposito**: base tecnica comum e ambiente local reproduzivel.

**Responsabilidades**
- Parent Maven: Java 25 (`--release 25`), Maven Enforcer, Spotless, JaCoCo (80% domain/application, 50% infrastructure), ArchUnit, gerenciamento de versoes (Spring Boot compativel com Java 25, AWS SDK v2, Resilience4j, Micrometer, Testcontainers).
- Modulo `platform-starter` (decisao do NFR Design de U1, Q1-A): auto-configuracao Spring Boot comum (logs ECS, Actuator minimo, tags de metricas com politica de tenant, tracing OTLP, `ProblemDetail` global, graceful shutdown); usado por `ingestion-api`, `worker` e `mock-legacy`.
- Modulo `shared`: `Cpf`, `IdempotencyKey`, `TenantId`, `Money`, `ConvenioType`, `Lane`, `ProposalMessage`, constantes de headers/trace e de atributos do inbox.
- `compose.yaml` (perfis `infra` e `apps`): DynamoDB Local, ElasticMQ, local-kms, PostgreSQL, Prometheus, Grafana, Jaeger, emissor JWT mock; healthchecks e limites de recursos.
- Configuracao base de observabilidade (Prometheus scrape, datasource Grafana, OTLP para Jaeger).

**Componentes**: C1 (shared) e C6 (parte local).
**Decisao a fechar**: versao exata do Spring Boot (NFR Requirements).

## U2. mock-legacy

**Proposito**: Core Banking simulado, pre-requisito de testes de Worker e k6.

**Responsabilidades**: `AverbacaoController`, `CapacityGate` (180 TPS), `IdempotencyStore`, `ChaosProperties`, metricas `legacy.request.throughput`/`latency`, contador de pico por janela de 1 s.
**Requisitos**: FR-5.1 a FR-5.4, NFR-4.
**Componentes**: C5, servico S5.

## U3. ingestion-api

**Proposito**: borda de absorcao multi-tenant.

**Responsabilidades**: `POST /v1/proposals`, JWT (JWKS), cota por tenant (Resilience4j, local por instancia), escrita condicional no inbox, protecao do payload (compressao + KMS), `ProblemDetail`, OpenAPI, metricas e tracing.
**Requisitos**: FR-1, FR-2 (escrita), NFR-1, 2, 3, 5, 6, 8, 9.
**Componentes**: C2, servico S1.

## U4. dispatcher-lambda

**Proposito**: roteamento por QoS.

**Responsabilidades**: `InboxStreamHandler`, `PriorityScorer`, `LaneRouter`, `QueuePublisher`, `InboxStatusUpdater`, configuracao de regras por ambiente, falha parcial de batch.
**Requisitos**: FR-3, NFR-14 (PBT: prioridade).
**Componentes**: C3, servico S2.
**Decisao a fechar**: runtime Lambda Java 25 gerenciado ou imagem de container (Infrastructure Design).

## U5. worker

**Proposito**: alimentar o legado a 180 TPS sem duplicidade e persistir estado.

**Responsabilidades**: `SqsLaneConsumer`, `LaneScheduler`, `LaneBudgetAllocator`, `FixedPerTaskBudgetAdapter`, `ProcessProposalService`, `LegacyHttpClient` (circuit breaker), `JpaProposalRepository` + Flyway, outbox como historico, DLQ apos 3 tentativas.
**Requisitos**: FR-4, FR-6, NFR-4, 5, 7, 10, 14 (PBT: alocador 70/30).
**Componentes**: C4, servicos S3 e S4.
**Decisoes a fechar**: estrategia do teto global (NFR Design); estados e transicoes de `proposal` (Functional Design).

## U6. infrastructure-terraform

**Proposito**: IaC pronto para AWS, validado sem deploy.

**Responsabilidades**: modulos WAF (IP e `X-Tenant-ID`), ALB (TLS 1.3), ECS Fargate (Ingestion, Worker), DynamoDB (Streams, TTL, SSE-KMS), Lambda, SQS (fast, standard, DLQ 14 dias), Aurora Serverless v2, KMS, IAM de minimo privilegio, CloudWatch; ambiente `poc` em `sa-east-1`, prefixo `consignado-poc`; `terraform validate` e `plan`.
**Requisitos**: FR-8, NFR-8.
**Componentes**: C6 (Terraform).

## U7. load-tests-and-dashboards

**Proposito**: evidencias do DoD.

**Responsabilidades**: k6 com 4 cenarios (stress, idempotencia 50.000 e smoke 500, throttling, drenagem), thresholds de p95/p99, verificacao SQL no PostgreSQL, 3 dashboards Grafana.
**Requisitos**: FR-7.2, FR-9.2, NFR-1 a 7.
**Componentes**: C6 (k6, Grafana).

## Estrategia de Organizacao de Codigo (greenfield)

Codigo na raiz da workspace (`applications/`); documentacao somente em `aidlc-docs/`.

```
applications/
  pom.xml                         (parent Maven)
  compose.yaml
  README.md
  shared/
  platform-starter/
  ingestion-api/
  dispatcher-lambda/
  worker/
  mock-legacy/
  infrastructure/
    terraform/
      modules/                    (waf, alb, ecs, dynamodb, lambda, sqs, aurora, kms, iam)
      envs/poc/
  observability/
    prometheus/
    grafana/dashboards/
  load-tests/
    k6/
```

Estrutura interna dos modulos Hexagonais (`ingestion-api`, `worker`):

```
src/main/java/br/com/consignado/platform/<servico>/
  domain/{model,service,exception}
  application/{port/in,port/out,usecase}
  infrastructure/{adapter/in,adapter/out,config}
src/test/java/...                 (unitarios, ArchUnit, integracao com Testcontainers)
```

`dispatcher-lambda` e `mock-legacy` usam pacotes planos por responsabilidade (`handler`, `routing`, `config`; `web`, `capacity`, `config`).

## Validacao das Fronteiras
- Cada unidade e desenvolvivel e testavel isoladamente: U2 e dependencia de teste do Worker; U1 e dependencia de build das demais.
- Nenhuma dependencia circular (ver `unit-of-work-dependency.md`).
- Todo componente de `components.md` pertence a exatamente uma unidade.
