# Components

Pacote base e groupId: `br.com.consignado.platform` (Q6-A). Java 25, Maven multi-modulo.
Ingestion API e Worker usam Hexagonal (`domain` -> `application` -> `infrastructure`) com ArchUnit; Dispatcher e Mock Legacy usam estrutura simples (D9).

## Visao Geral dos Modulos

| Modulo | Estilo | Responsabilidade |
|---|---|---|
| `shared` | Biblioteca (sem Spring) | Value objects e contratos de mensagem comuns (Q2-B) |
| `platform-starter` | Auto-configuracao Spring Boot | Observabilidade, erros e hardening comuns a Ingestion API, Worker e Mock Legacy (adicionado no NFR Design de U1) |
| `ingestion-api` | Hexagonal | Borda: autenticacao, validacao, cota por tenant, escrita idempotente, resposta 202 |
| `dispatcher-lambda` | Simples | Le DynamoDB Streams, calcula `priorityScore`, roteia para SQS (Q1-A) |
| `worker` | Hexagonal | Consome SQS, orcamento 70/30, chama legado, persiste estado e outbox |
| `mock-legacy` | Simples | Core Banking simulado (180 TPS, falhas configuraveis, idempotencia) |

## C1. shared (`br.com.consignado.platform.shared`)

**Proposito**: eliminar divergencia de validacao e de contrato entre servicos.

**Responsabilidades**
- Value objects imutaveis (`record`, validacao na construcao): `Cpf`, `IdempotencyKey`, `TenantId`, `Money`.
- Enums de contrato: `ConvenioType` (`INSS`, `ESOCIAL_CLT`, `SIAPE_PUBLICO`), `Lane` (`FAST`, `STANDARD`).
- Contrato de mensagem entre Dispatcher e Worker: `ProposalMessage` (versionado, sem logica).
- Constantes de headers (`Idempotency-Key`, `X-Tenant-ID`, `traceparent`) e chaves de propagacao de trace em mensagens.

**Restricoes**: sem Spring, sem AWS SDK, sem logica de negocio; `Cpf.toString()` mascarado (nunca expor PII em log).

## C2. ingestion-api (`...platform.ingestion`)

### Domain
- `IngestedProposal` (entidade/VO agregador): `IdempotencyKey`, `TenantId`, `Cpf`, `ConvenioType`, `portability`, `Money`, `installments`, `receivedAt`.
- `IngestionOutcome` (enum): `RECEIVED`, `DUPLICATE_ACCEPTED`.
- Excecoes: `InvalidProposalException`, `TenantQuotaExceededException`.

### Application
- Port in: `AcceptProposalUseCase`.
- Ports out: `DurableInboxPort` (escrita condicional), `TenantQuotaPort`, `PayloadProtectionPort` (compressao + cifra do payload), `TraceContextPort`, `ClockPort`.
- Use case: `AcceptProposalService`.

### Infrastructure
- In/web: `ProposalController` (`POST /v1/proposals`), `ProposalRequest`/`ProposalAcceptedResponse` (DTOs), `ProblemDetailExceptionHandler` (RFC 7807), `TenantHeaderFilter`.
- In/security: `JwtSecurityConfig` (Resource Server, JWKS configuravel, Q5-A).
- Out: `DynamoDbInboxAdapter`, `Resilience4jTenantQuotaAdapter` (cotas estaticas, local por instancia, Q6 dos requisitos), `AesKmsPayloadProtectionAdapter`.
- Config: `OpenApiConfig`, metricas Micrometer (`ingestion.accepted.rate`, `ingestion.throttled.tenant.rate`).

**Interface externa**: `POST /v1/proposals` -> `202 {status, idempotencyKey, tracingId, receivedAt}`; `400`, `401`, `429`.

## C3. dispatcher-lambda (`...platform.dispatcher`)

Estrutura simples (sem hexagonal completo), classes pequenas com injecao por construtor.

- `InboxStreamHandler`: entrada Lambda; processa registros INSERT do stream (ignora MODIFY/REMOVE).
- `PriorityScorer` (funcao pura): calcula `priorityScore` por portabilidade, parceiro VIP ou valor acima do limiar (D10); resultado numerico.
- `LaneRouter` (funcao pura): `priorityScore` >= limiar de lane -> `Lane.FAST`, senao `Lane.STANDARD`.
- `PriorityRulesProperties`: lista VIP, limiar de valor e limiar de lane (variaveis de ambiente).
- `QueuePublisher`: publica `ProposalMessage` em `sqs-fast-lane-queue` ou `sqs-standard-lane-queue`, com propagacao de `traceparent` nos atributos da mensagem.
- `InboxStatusUpdater`: grava `priorityScore` e `status = DISPATCHED` no item do inbox.

**Nota de desenho**: o item do `DurableInbox` guarda os atributos de roteamento fora do payload cifrado (`tenantId`, `convenioType`, `portability`, `amount`), para que o Dispatcher nao precise decifrar o CPF.

## C4. worker (`...platform.worker`)

### Domain
- `Proposal` (agregado): maquina de estados (`PENDING`, `SENT_TO_LEGACY`, `CONFIRMED`, `FAILED`; transicoes finais no Functional Design) e `ProposalEvent` (registro do outbox).
- `LaneBudgetAllocator` (domain service, funcao pura): decide a proxima lane com piso 126/54 e emprestimo de capacidade ociosa, teto 180 (Q3-A).
- `LegacyResult` (value object): `CONFIRMED` / `REJECTED` / `TRANSIENT_FAILURE`.

### Application
- Port in: `ProcessProposalUseCase`.
- Ports out: `ProposalRepositoryPort` (inclui gravacao de eventos na mesma transacao), `LegacyCorePort`, `ThroughputBudgetPort` (Q4-A), `QueueAckPort`, `ClockPort`.
- Use case: `ProcessProposalService`; `LaneScheduler` (decide qual lane consumir usando `LaneBudgetAllocator` e `ThroughputBudgetPort`).

### Infrastructure
- In/messaging: `SqsLaneConsumer` (polling com Virtual Threads, uma instancia por lane).
- Out/persistence: `JpaProposalRepository`, entidades JPA separadas do dominio, migrations Flyway (`proposal`, `proposal_event`).
- Out/client: `LegacyHttpClient` com Resilience4j CircuitBreaker (latencia > 2.000 ms ou 5xx > 10%) e `Idempotency-Key` (Q5-A); sem retry cego.
- Out/budget: `FixedPerTaskBudgetAdapter` (180 / N tarefas, N configuravel).
- Config: metricas (`legacy.request.latency`, `net_backlog_growth`, `effective_drain_time`), tracing, graceful shutdown.

## C5. mock-legacy (`...platform.legacy`)

- `AverbacaoController`: `POST /v1/averbacoes` com `Idempotency-Key` obrigatorio.
- `CapacityGate`: janela deslizante de 1 s; acima de 180 TPS responde `503`.
- `IdempotencyStore`: chave repetida devolve o mesmo resultado (`200`), em memoria e com limite.
- `ChaosProperties`: latencia e taxa de erro configuraveis (testa circuit breaker).
- Metricas: `legacy.request.throughput`, `legacy.request.latency`; contador de pico por janela de 1 s e de averbacoes duplicadas (esperado zero).

## C6. Componentes de plataforma (nao-Java)

| Componente | Unidade | Descricao |
|---|---|---|
| Terraform modules | U6 | WAF, ALB, ECS, DynamoDB (+Streams, TTL), Lambda, SQS, Aurora, KMS, IAM |
| `compose.yaml` | U1 | DynamoDB Local, ElasticMQ, local-kms, PostgreSQL, Prometheus, Grafana, Jaeger, emissor JWT mock, servicos |
| Scripts k6 | U7 | Stress, idempotencia (50.000 e smoke 500), throttling, drenagem |
| Dashboards Grafana | U7 | Ingestion Health, Protecao Downstream, Backlog & Drenagem |
