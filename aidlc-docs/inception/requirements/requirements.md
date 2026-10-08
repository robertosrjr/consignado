# Requirements Document - PoC Credito Consignado (Ingestao Massiva e Resiliencia)

## 1. Intent Analysis Summary

- **User Request**: "analisando os arquivos que estao dentro da pasta sdd, faca a contextualizacao da nossa atividade e vamos iniciar o processo utilizando a metodologia ia-dlc"
- **Request Type**: New Project (greenfield)
- **Scope Estimate**: System-wide (5 servicos/modulos + IaC + observabilidade + testes de carga)
- **Complexity Estimate**: Complex (NFRs extremos, idempotencia financeira, multi-tenant, QoS)
- **Requirements Depth**: Comprehensive
- **Fontes**: `sdd/sdd-credito-consignado-poc.md` (SDD), `sdd/especificacao-ai-dlc-poc.md`, `sdd/blueprint-implementacao-poc.md`, `sdd/narrativas/*`
- **Respostas do usuario**: `requirement-verification-questions.md` (Q1-Q17, todas aceitas conforme sugestoes; sem contradicoes)
- **Decisao fixa do usuario**: Java 25 LTS (substitui Java 21 dos documentos)

## 2. Business Context

- Na "virada de folha" (INSS, eSocial/CLT, servidores) o trafego salta de 50 para 10.000 TPS; o Core Banking legado suporta 180 TPS.
- Arquitetura sincrona causa exaustao de conexoes, downtime, perda de originacao, averbacao duplicada e risco de sancao Febraban/ABBC.
- **Tese da PoC**: Borda de Absorcao com Desacoplamento Temporal: aceitar tudo com durabilidade e idempotencia imediatas e alimentar o legado em "conta-gotas" (<= 180 TPS) com QoS 70/30.
- **Stakeholders**: Comite de Arquitetura / C-Level (homologacao), Engenharia, SRE/DevOps, parceiros/correspondentes (consumidores da API).

## 3. Decisoes Consolidadas

| Ref | Decisao | Origem |
|---|---|---|
| D1 | Runtime **Java 25 LTS**; Spring Boot em versao compativel com Java 25 (3.5.x ultima patch; fallback 4.0.x), validada na etapa NFR Requirements | Usuario |
| D2 | Escopo: codigo + Terraform completos; validacao local (Docker Compose + emuladores sem conta: DynamoDB Local, ElasticMQ, local-kms); Terraform so `validate/plan`, **sem deploy**; k6 em escala reduzida local, script pronto para escala cheia | Q1-B |
| D3 | Teto do legado **180 TPS** (default configuravel): Fast 126 / Standard 54 | Q2-A |
| D4 | Contrato hibrido: convenio `INSS`, `ESOCIAL_CLT`, `SIAPE_PUBLICO`; `priorityScore` numerico; respostas `RECEIVED` / `DUPLICATE_ACCEPTED`; `tracingId` | Q3-C |
| D5 | CPF em claro apenas em repouso criptografado (KMS); zero PII em logs, metricas e traces | Q4-A |
| D6 | JWT com validacao real (assinatura, issuer, expiracao) via JWKS configuravel; emissor mock no ambiente local | Q5-A |
| D7 | Cotas por tenant estaticas (config/env, com default); Resilience4j na aplicacao (local por instancia, documentado) + WAF no Terraform | Q6-A |
| D8 | Maven multi-modulo: `parent`, `ingestion-api`, `dispatcher-lambda`, `worker`, `mock-legacy`, `shared` | Q7-A |
| D9 | Hexagonal + DDD + ArchUnit em Ingestion API e Worker; Dispatcher e Mock Legacy simples | Q8-B |
| D10 | Prioridade deterministica: portabilidade OU parceiro VIP (lista configuravel) OU valor acima de limiar configuravel; resultado vira `priorityScore` | Q9-A |
| D11 | Worker grava `proposal` como `PENDING` antes de chamar o legado, chama o legado com chave de idempotencia e atualiza o estado; `proposal_event` em cada transicao; retry SQS e DLQ apos 3 tentativas | Q10-B |
| D12 | Outbox so registra historico auditavel (sem relay/publicador) | Q11-A |
| D13 | Observabilidade: Micrometer + Prometheus + Grafana (3 paineis) + OpenTelemetry/Jaeger com `traceparent` ponta a ponta; sem Alertmanager/Pushgateway | Q12-A |
| D14 | Sem simuladores Dataprev/eSocial/eConsig; apenas Mock Legacy | Q13-B |
| D15 | Idempotencia: 50.000 requisicoes concorrentes (DoD) + 500 como smoke | Q14-A |
| D16 | Extensao **Security Baseline: ATIVA** (regras SECURITY bloqueantes) | Q15-A |
| D17 | Extensao **Property-Based Testing: PARCIAL** (funcoes puras e round-trips de serializacao) | Q16-B |
| D18 | Regiao `sa-east-1`, ambiente `poc`, prefixo `consignado-poc` | Q17-A |

## 4. Arquitetura Alvo (resumo)

```
+------------------------------------------------------+
| Parceiros / Robos (10.000 TPS)                       |
+--------------------------+---------------------------+
                           |
                           v
+------------------------------------------------------+
| WAF v2 -> ALB -> Ingestion API (ECS Fargate)         |
| JWT, cota por tenant, HTTP 202 p95 < 100 ms          |
+--------------------------+---------------------------+
                           | PutItem condicional
                           v
+------------------------------------------------------+
| DynamoDB DurableInbox (idempotencia + TTL 7 dias)    |
+--------------------------+---------------------------+
                           | DynamoDB Streams
                           v
+------------------------------------------------------+
| Lambda Dispatcher (prioridade -> Fast ou Standard)   |
+--------------------------+---------------------------+
                           v
+------------------------------------------------------+
| SQS: fast-lane (70%) | standard-lane (30%) | DLQ     |
+--------------------------+---------------------------+
                           v
+------------------------------------------------------+
| Worker (ECS Fargate) - rate limit 180 TPS + breaker  |
+------------+-----------------------------+-----------+
             |                             |
             v                             v
+--------------------------+   +-----------------------+
| Mock Legacy (<= 180 TPS) |   | Aurora PostgreSQL     |
|                          |   | proposal + outbox     |
+--------------------------+   +-----------------------+
```

## 5. Functional Requirements

### FR-1 Ingestion API (`POST /v1/proposals`)
- **FR-1.1** Headers obrigatorios: `Authorization: Bearer <JWT>`, `Idempotency-Key` (UUID), `X-Tenant-ID`. Ausente ou invalido: `400`; JWT invalido: `401`.
- **FR-1.2** Body `ProposalRequest`: `customerCpf`, `convenioCode` (`INSS`, `ESOCIAL_CLT`, `SIAPE_PUBLICO`), `isPortability`, `amount`, `installments`. Validacao de schema e dominio (CPF valido, valor e parcelas positivos).
- **FR-1.3** Verificacao de cota do tenant antes da escrita; excedida: `429` com `Retry-After`, sem afetar outros tenants.
- **FR-1.4** Escrita condicional no DynamoDB (`attribute_not_exists(idempotencyKey)`).
- **FR-1.5** Sucesso: `202` com `{status: "RECEIVED", idempotencyKey, tracingId, receivedAt}`.
- **FR-1.6** Duplicidade (`ConditionalCheckFailedException`): `202` com `status: "DUPLICATE_ACCEPTED"` e mesmo `tracingId`, sem reenfileirar.
- **FR-1.7** Erros em RFC 7807 (`ProblemDetail`), sem stack trace, SQL ou nomes internos.
- **FR-1.8** Contrato OpenAPI unico, versionado (`/v1`), com springdoc (`@Tag`, `@Operation`, `@ApiResponse`, `@Schema`). Contrato consolidado substitui as divergencias dos documentos de origem.

### FR-2 Durable Inbox (DynamoDB)
- **FR-2.1** Tabela `DurableInbox`, PK `idempotencyKey`; atributos: `tenantId`, `convenioType`, `priorityScore`, `payload` (JSON comprimido), `status` (`RECEIVED`, `DISPATCHED`, `FAILED`), `createdAt`, `ttl` (7 dias).
- **FR-2.2** Payload com CPF criptografado em repouso (KMS/SSE).

### FR-3 Dispatcher (Lambda + DynamoDB Streams)
- **FR-3.1** Acionado por evento INSERT do stream; ignora modificacoes e remocoes (TTL).
- **FR-3.2** Calcula `priorityScore` conforme D10 e roteia para `sqs-fast-lane-queue` ou `sqs-standard-lane-queue`.
- **FR-3.3** Atualiza `status` para `DISPATCHED`; falha de envio resulta em retry do stream/DLQ, sem perda.
- **FR-3.4** Regras de prioridade (VIP, limiar de valor) configuraveis por ambiente.

### FR-4 Worker Fleet
- **FR-4.1** Consome as duas filas SQS com Virtual Threads, respeitando orcamento global 180 TPS: Fast 126 / Standard 54 (sem starvation; capacidade ociosa de uma lane pode ser cedida, se a regra for definida no design).
- **FR-4.2** Fluxo por mensagem: gravar `proposal` `PENDING` (idempotente por `idempotency_key UNIQUE`) -> chamar o legado com chave de idempotencia -> atualizar estado -> registrar `proposal_event` em cada transicao.
- **FR-4.3** Circuit breaker no legado: abre com latencia > 2.000 ms ou erros 5xx > 10%; sem retry storm.
- **FR-4.4** Retry apenas onde seguro (operacao idempotente); apos 3 tentativas a mensagem vai para `sqs-dlq` (retencao 14 dias).
- **FR-4.5** Drenagem autonoma do backlog apos o pico, mantendo a proporcao 70/30.

### FR-5 Mock Legacy
- **FR-5.1** Simula o Core Banking com capacidade maxima de 180 TPS; acima do limite responde `503` ou degrada de forma controlada.
- **FR-5.2** Expoe `legacy.request.throughput` e `legacy.request.latency`; permite medir pico por janela de 1 segundo.
- **FR-5.3** Latencia e taxa de erro configuraveis para testar circuit breaker.
- **FR-5.4** Deduplica por chave de idempotencia (nenhuma averbacao duplicada).

### FR-6 Estado Relacional (Aurora PostgreSQL)
- **FR-6.1** Tabelas `proposal` e `proposal_event` (DDL do SDD, ajustado ao contrato D4), migrations com Flyway.
- **FR-6.2** `proposal.status` com maquina de estados: `PENDING`, `SENT_TO_LEGACY`/`CONFIRMED`, `FAILED` (estados exatos definidos no Functional Design).
- **FR-6.3** `proposal_event` registra historico auditavel (somente tabela, D12).

### FR-7 Observabilidade
- **FR-7.1** Metricas: `ingestion.accepted.rate`, `ingestion.throttled.tenant.rate` (tag de tenant com cardinalidade controlada), `net_backlog_growth`, `effective_drain_time`, `legacy.request.throughput`, `legacy.request.latency` (p50/p95/p99).
- **FR-7.2** Tres paineis Grafana: Ingestion Health, Protecao Downstream, Backlog & Drenagem.
- **FR-7.3** Tracing W3C (`traceparent`) da borda ao legado (inclusive via SQS/Streams, por atributos de mensagem), amostragem configuravel; coletor opcional em testes locais.
- **FR-7.4** Logs estruturados (SLF4J, stdout), correlacao por `traceId` via MDC, sem payload e sem PII.

### FR-8 Infraestrutura como Codigo (Terraform)
- **FR-8.1** Modulos para: WAF v2 (rate limiting por IP e por header `X-Tenant-ID`), ALB (HTTPS TLS 1.3), ECS Fargate (Ingestion e Worker), DynamoDB (+Streams, TTL), Lambda, SQS (fast, standard, DLQ), Aurora Serverless v2, IAM de minimo privilegio, KMS, CloudWatch/log groups.
- **FR-8.2** Regiao `sa-east-1`, ambiente `poc`, prefixo `consignado-poc`.
- **FR-8.3** `terraform validate` e `plan` executaveis (sem `apply` nesta entrega).

### FR-9 Ambiente Local e Testes de Carga
- **FR-9.1** `compose.yaml` com DynamoDB Local (Streams), ElasticMQ (SQS), local-kms (KMS), PostgreSQL, Prometheus, Grafana, Jaeger, emissor JWT mock e os servicos; healthchecks e limites de recursos.
- **FR-9.2** Script k6 com 4 cenarios: stress (10.000 TPS x 5 min na escala configuravel), idempotencia (50.000 concorrentes; smoke de 500), throttling de tenant e drenagem pos-pico.

## 6. Non-Functional Requirements

| Id | Requisito | Meta | Verificacao |
|---|---|---|---|
| NFR-1 | Pico sustentado na borda | 10.000 TPS por 5 min (~3.000.000 req) | k6 (escala reduzida local; cheia = script pronto) |
| NFR-2 | Latencia da borda | p95 < 100 ms, p99 < 200 ms em `202` | k6 thresholds |
| NFR-3 | Disponibilidade na borda durante o pico | > 99,9% `202` | k6 |
| NFR-4 | Protecao do legado | <= 180 TPS em qualquer janela de 1 s | Mock Legacy + Prometheus |
| NFR-5 | Idempotencia | 50.000 requisicoes iguais -> exatamente 1 registro no PostgreSQL e 0 averbacoes duplicadas | k6 + SQL |
| NFR-6 | Isolamento multi-tenant | Tenant acima da cota recebe `429`; demais mantem `202` | k6 |
| NFR-7 | Drenagem do backlog | Autonoma, proporcao 70/30 | k6 + metricas |
| NFR-8 | Seguranca (extensao Security Baseline ativa) | Sem segredos hardcoded, TLS 1.3, queries parametrizadas, JWT validado, erros sem vazamento, IAM minimo privilegio, criptografia em repouso e em transito | Revisao `security-code-review` + gates da extensao |
| NFR-9 | Privacidade (LGPD) | Zero PII em logs, metricas e traces; retencao: TTL 7 dias na inbox | Skill `lgpd-sre-compliance-skill`, testes de inventario de telemetria |
| NFR-10 | Resiliencia | Sem retry cego em escrita financeira; filas e estado local limitados; protecoes documentadas como locais ou distribuidas | Skill `resilience-checker-skill`, testes de falha/concorrencia/replay |
| NFR-11 | Twelve-Factor | Configuracao por env, stateless, graceful shutdown, health liveness/readiness | Revisao `platform-practices-guidance` |
| NFR-12 | Qualidade e arquitetura | SOLID/Clean Code, Spotless, ArchUnit (regra de dependencia domain -> application -> infrastructure) | Build com falha em violacao |
| NFR-13 | Testes | Piramide 70/25/5; cobertura >= 80% em domain e application, >= 50% em infrastructure; Testcontainers (sem H2); TDD | JaCoCo no build |
| NFR-14 | PBT parcial | Propriedades para prioridade, divisao de orcamento 70/30 e round-trip de serializacao/compressao | Regras da extensao PBT (modo parcial) |
| NFR-15 | Plataforma | Java 25, MVC + Virtual Threads (nao misturar com WebFlux), compilacao `--release 25` | Maven Enforcer |

## 7. Escopo

**Dentro**: Ingestion API, DurableInbox, Dispatcher, Worker, Mock Legacy, Aurora (`proposal`, `proposal_event`), observabilidade (D13), Terraform validado, Docker Compose, k6, documentacao (README, ADRs).

**Fora (nesta entrega)**: deploy na AWS, teste de 10.000 TPS em nuvem, simuladores Dataprev/eSocial/eConsig, relay do outbox, Alertmanager/Pushgateway, cotas dinamicas em tabela, score ponderado com rating/LTV reais.

## 8. Premissas e Riscos

| Id | Premissa / Risco | Tratamento |
|---|---|---|
| A1 | Spring Boot compativel com Java 25 | Validar versao exata no NFR Requirements; fallback 4.0.x |
| A2 | Runtime Lambda Java 25 gerenciado pode nao estar disponivel na regiao | Alternativa: imagem de container no Lambda (decisao no Infrastructure Design) |
| A3 | Limiter na aplicacao e local por instancia; o teto global de 180 TPS exige coordenacao entre workers | Definir no NFR Design (ex.: numero fixo de tarefas, orcamento por tarefa ou contador distribuido) |
| A4 | Emuladores locais (DynamoDB Local, ElasticMQ, local-kms) nao replicam WAF, Aurora Serverless v2, throttling do DynamoDB, nem Lambda; o LocalStack foi descartado por exigir auth token (gratuito so para uso nao comercial) desde 2026.03.0; o Dispatcher roda localmente por `local-runner` | PostgreSQL local no lugar do Aurora; testes de integracao com Testcontainers; WAF so validado via Terraform |
| A5 | 10.000 TPS reais exigem infraestrutura que a maquina local nao tem | k6 com escala parametrizavel; extrapolacao documentada |
| A6 | Chave de idempotencia aceita pelo Mock Legacy para evitar averbacao dupla | Parte do contrato do Mock Legacy (FR-5.4) |
| A7 | Estados e transicoes de `proposal` ainda nao detalhados | Functional Design (Worker) |

## 9. Rastreabilidade (DoD -> Requisitos)

| DoD (SDD) | Requisitos |
|---|---|
| 1. Carga sustentada 10k TPS, p95 < 100 ms, > 99,9% | NFR-1, NFR-2, NFR-3, FR-1, FR-9.2 |
| 2. Legado <= 180 TPS | NFR-4, FR-4.1, FR-4.3, FR-5.1, FR-5.2 |
| 3. Zero duplicacao (50.000 iguais -> 1 registro) | NFR-5, FR-1.4, FR-1.6, FR-4.2, FR-5.4, FR-6.1 |
| 4. Resiliencia multi-tenant (429 isolado) | NFR-6, FR-1.3, FR-8.1 |
| 5. Drenagem autonoma 70/30 | NFR-7, FR-4.1, FR-4.5, FR-7.1 |

## 10. Resumo

Sistema critico, greenfield, em 5 modulos Java 25 mais Terraform, validado localmente. Os pontos de maior risco sao a idempotencia ponta a ponta sem averbacao duplicada, o teto global de 180 TPS com varios workers e a fidelidade do ambiente local frente a AWS. As extensoes Security Baseline (bloqueante) e Property-Based Testing (parcial) estao ativas.
