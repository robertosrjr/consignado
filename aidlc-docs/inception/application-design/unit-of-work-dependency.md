# Unit of Work Dependencies

## Matriz de Dependencias

Legenda: B = build (compile-time), T = teste/integracao, R = runtime, C = contrato (sem compilacao), - = nenhuma. Linha depende da coluna.

| Unidade | U1 foundation | U2 mock-legacy | U3 ingestion-api | U4 dispatcher | U5 worker | U6 terraform | U7 load-tests |
|---|---|---|---|---|---|---|---|
| U1 | - | - | - | - | - | - | - |
| U2 | B | - | - | - | - | - | - |
| U3 | B | - | - | - | - | - | - |
| U4 | B | - | C (item do inbox) | - | - | - | - |
| U5 | B | T, R | C (item do inbox) | C (ProposalMessage) | - | - | - |
| U6 | - | - | C (portas, env vars) | C (runtime, env vars) | C (portas, env vars) | - | - |
| U7 | - | R | R | R | R | - | - |

Sem dependencias circulares. U1 e raiz; U7 e folha.

## Pontos de Integracao e Contratos

| Contrato | Produtor | Consumidor | Formato | Onde vive |
|---|---|---|---|---|
| API `POST /v1/proposals` | U3 | Parceiros, U7 | OpenAPI 3 | `ingestion-api` (springdoc), versionada em `/v1` |
| Item `DurableInbox` | U3 (escreve), U4 (atualiza) | U4, U5 (le) | Atributos DynamoDB | Constantes de nomes em `shared` (U1) |
| `ProposalMessage` | U4 | U5 | JSON versionado (`schemaVersion`) | `shared` (U1) |
| API `POST /v1/averbacoes` | U2 | U5 | JSON + `Idempotency-Key` | `mock-legacy` (OpenAPI proprio) |
| Tabelas `proposal`, `proposal_event` | U5 | U7 (verificacao SQL) | DDL Flyway | `worker` |
| Metricas Prometheus | U2, U3, U5 | U7 (dashboards) | Nomes do `requirements.md` FR-7.1 | Cada servico |
| Variaveis de ambiente | U3, U4, U5 | U6 (Terraform), compose (U1) | `application.yml` externalizado | Cada servico documenta |

## Ordem de Build e Entrega

1. **U1** platform-foundation (parent, `shared`, compose, observabilidade base).
2. **U2** mock-legacy (necessario para integrar o Worker e para o k6).
3. **U3** ingestion-api.
4. **U4** dispatcher-lambda (consome o item gravado por U3).
5. **U5** worker (depende de U2, do contrato de U4 e do item de U3).
6. **U6** infrastructure-terraform (apos U3, U4 e U5 definirem portas, env vars e permissoes).
7. **U7** load-tests-and-dashboards (valida a cadeia completa).

**Paralelizacao possivel** (sem prejuizo ao fluxo de aprovacao por estagio): U2 e U3 apos U1; U6 pode ter o design iniciado depois de U5.

## Restricoes de Build (Maven)

- Reactor unico no `applications/pom.xml`, modulos na ordem: `shared`, `platform-starter`, `mock-legacy`, `ingestion-api`, `dispatcher-lambda`, `worker`.
- `platform-starter` depende apenas de `shared` e e usado por `mock-legacy`, `ingestion-api` e `worker`; `dispatcher-lambda` nao o usa (sem Spring).
- `shared` nao depende de nenhum servico; nenhum servico depende de outro servico em compile-time.
- Gate de qualidade no build: Spotless, Enforcer (Java 25), ArchUnit (U3 e U5), JaCoCo.

## Estrategia de Teste de Integracao entre Unidades

| Par | Teste | Ferramenta |
|---|---|---|
| U3 -> DynamoDB | Escrita condicional e duplicidade | Testcontainers (DynamoDB Local) |
| U4 -> SQS/DynamoDB | Roteamento e atualizacao condicional | Testcontainers (ElasticMQ + DynamoDB Local) |
| U5 -> Aurora | `createPendingIfAbsent`, outbox | Testcontainers (PostgreSQL), sem H2 |
| U5 -> U2 | Circuit breaker, idempotencia no legado | Testcontainers com imagem do Mock Legacy ou `MockRestServiceServer` |
| Cadeia completa | DoD (k6 + SQL) | Compose + U7 |
