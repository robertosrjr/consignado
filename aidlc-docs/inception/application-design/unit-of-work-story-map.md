# Unit of Work Requirement Map

User Stories foi **pulado** no Workflow Planning. Como substituto, este mapa atribui cada requisito (FR/NFR) de `requirements.md` a exatamente uma unidade **responsavel**, com unidades de apoio indicadas separadamente. Cobertura verificada ao final.

## Mapa Requisito -> Unidade

| Requisito | Descricao resumida | Unidade responsavel | Apoio |
|---|---|---|---|
| FR-1.1 a FR-1.8 | Ingestion API (headers, validacao, cota, escrita condicional, 202/DUPLICATE, ProblemDetail, OpenAPI) | U3 | U1 (`shared`) |
| FR-2.1 | Esquema `DurableInbox` | U3 | U4, U5 (consumo), U1 (constantes) |
| FR-2.2 | CPF cifrado em repouso | U3 | U6 (KMS) |
| FR-3.1 a FR-3.4 | Dispatcher | U4 | U1 |
| FR-4.1 a FR-4.5 | Worker, orcamento, circuit breaker, DLQ, drenagem | U5 | U2, U1 |
| FR-5.1 a FR-5.4 | Mock Legacy | U2 | - |
| FR-6.1 a FR-6.3 | Aurora, estados, outbox | U5 | - |
| FR-7.1 | Metricas | U2, U3, U5 (cada uma as suas) | U1 (base) |
| FR-7.2 | Dashboards Grafana | U7 | U1 (datasource) |
| FR-7.3 | Tracing W3C | U1 (config base) | U3, U4, U5 (propagacao) |
| FR-7.4 | Logs estruturados sem PII | U1 (padrao) | U2, U3, U4, U5 |
| FR-8.1 a FR-8.3 | Terraform | U6 | - |
| FR-9.1 | `compose.yaml` | U1 | - |
| FR-9.2 | Script k6 | U7 | - |
| NFR-1, 2, 3 | Pico, latencia, disponibilidade | U7 (verificacao) | U3 |
| NFR-4 | Protecao do legado | U5 (controle) | U2 (medicao), U7 |
| NFR-5 | Idempotencia | U3 | U5, U2, U7 |
| NFR-6 | Isolamento multi-tenant | U3 | U6 (WAF), U7 |
| NFR-7 | Drenagem 70/30 | U5 | U7 |
| NFR-8 | Seguranca | U3 | U4, U5, U6 |
| NFR-9 | LGPD | U1 (padrao) | U3, U4, U5 |
| NFR-10 | Resiliencia | U5 | U3 |
| NFR-11 | Twelve-Factor | U1 (padrao) | U2, U3, U4, U5 |
| NFR-12 | Qualidade e ArchUnit | U1 (gates) | U3, U5 |
| NFR-13 | Testes e cobertura | U1 (JaCoCo) | todas |
| NFR-14 | PBT parcial | U4 (prioridade), U5 (alocador), U1 (round-trip VOs) | - |
| NFR-15 | Java 25, MVC + Virtual Threads | U1 | U2, U3, U5 |

## Ordem de Prioridade Dentro de Cada Unidade

| Unidade | Prioridade |
|---|---|
| U1 | 1. Parent Maven e gates; 2. `shared`; 3. `compose.yaml`; 4. Observabilidade base |
| U2 | 1. `CapacityGate` e controller; 2. Idempotencia; 3. Chaos; 4. Metricas |
| U3 | 1. Caso de uso e portas; 2. Escrita condicional; 3. JWT e erros; 4. Cota por tenant; 5. Protecao do payload; 6. Metricas e OpenAPI |
| U4 | 1. `PriorityScorer`/`LaneRouter`; 2. Handler e publisher; 3. Atualizacao do inbox; 4. Falha parcial |
| U5 | 1. Dominio e maquina de estados; 2. `ProcessProposalService`; 3. Persistencia e outbox; 4. Cliente do legado e breaker; 5. Scheduler e orcamento; 6. Consumo SQS e DLQ |
| U6 | 1. KMS, DynamoDB, SQS; 2. IAM; 3. ECS e ALB; 4. WAF; 5. Lambda e Aurora; 6. `validate`/`plan` |
| U7 | 1. Cenario de idempotencia; 2. Throttling; 3. Stress; 4. Drenagem; 5. Dashboards |

## Verificacao de Cobertura

- Todos os FR-1 a FR-9 e NFR-1 a NFR-15 aparecem no mapa com uma unidade responsavel: **cobertos**.
- Os 5 criterios do DoD do SDD (carga, protecao do legado, idempotencia, multi-tenant, drenagem) tem verificacao em U7 e controle nas unidades correspondentes: **cobertos**.
- Nenhum requisito sem dono; requisitos transversais (FR-7.3, FR-7.4, NFR-9, 11, 12, 13) tem a unidade U1 como dona do padrao e as demais como aplicadoras.
