# AI-DLC State Tracking

## Project Information
- **Project Name**: Platform Engine - PoC Credito Consignado (Ingestao Massiva e Resiliencia)
- **Project Type**: Greenfield
- **Start Date**: 2026-10-07T12:51:04Z
- **Current Stage**: CONSTRUCTION - aplicacao Java local construida e verde (U1-U5 + local-env); aguardando subir os servicos localmente. Terraform (U6) adiado; observabilidade (U7) parcial
- **Last Build**: 2026-10-08 - `mvn verify` BUILD SUCCESS, 9 modulos, 354 testes, 0 falhas; gates de cobertura (80%), Spotless, Enforcer e ArchUnit aprovados

## Workspace State
- **Existing Code**: No
- **Reverse Engineering Needed**: No
- **Workspace Root**: C:\Developer\Workspace\java\consignado
- **Source Documents**: `sdd/` (SDD, Especificacao AI-DLC, Blueprint, 2 narrativas)

## Code Location Rules
- **Application Code**: Workspace root (`applications/`), NEVER in `aidlc-docs/`
- **Documentation**: `aidlc-docs/` only
- **Rule details**: `.aidlc-lite-rule-details/`

## Stage Progress
### INCEPTION PHASE
- [x] Workspace Detection
- [x] Reverse Engineering (SKIPPED - greenfield)
- [x] Requirements Analysis (aprovado pelo usuario)
- [x] User Stories (SKIPPED)
- [x] Workflow Planning (aprovado)
- [x] Application Design (aprovado)
- [x] Units Generation (aprovado)

### CONSTRUCTION PHASE
- [ ] Per-Unit Loop (Functional Design / NFR Requirements / NFR Design / Infrastructure Design / Code Generation)
  - [ ] U1 platform-foundation: Functional Design SKIPPED; NFR Requirements COMPLETED; NFR Design COMPLETED; Infrastructure Design COMPLETED; Code Generation COMPLETED, aprovacao SUSPENSA: mudanca de escopo (sem Docker, banco em memoria, topologia em 3 EC2) em andamento: perguntas em no-docker-change-questions.md (Q1-Q6) e topologia-3-ec2-questions.md (T1-T7)
  - [ ] U2 mock-legacy: codigo COMPLETO e verde (capacidade 180/janela de 1s, idempotencia, caos, Swagger UI). Aprovacao PENDENTE
  - [ ] U3 ingestion-api: codigo COMPLETO e verde (202, idempotencia atomica no DynamoDB, cota por tenant, envelope AES-GCM, JWT, Swagger UI). Aprovacao PENDENTE
  - [ ] U4 dispatcher-lambda: codigo COMPLETO e verde (score de prioridade, roteamento 70/30, EMF no CloudWatch). Aprovacao PENDENTE
  - [ ] U5 worker: codigo COMPLETO e verde (orcamento de vazao com piso 70/30 e emprestimo, circuit breaker, estado/outbox em memoria). Aprovacao PENDENTE
  - [ ] U6 infrastructure-terraform: ADIADO por decisao do usuario; o usuario esta montando `infrastructure/` por conta propria (nao alterar)
  - [ ] U7 load-tests-and-dashboards: PARCIAL. Fonte CloudWatch provisionada no Grafana + politica IAM + mapa de metricas. Faltam: paineis JSON, scripts k6, regras de alerta definitivas
- [ ] Build and Test: PARCIAL. `mvn verify` verde; ITs do ingestion-api rodam com `-Pintegration` (DynamoDB Local embarcado). Faltam: ITs do worker, teste de pipeline ponta a ponta e subir os servicos localmente (`scripts/local-up.ps1` ainda nao concluiu uma subida completa)

### OPERATIONS PHASE
- [ ] Operations (PLACEHOLDER)

## Extension Configuration
- Security Baseline: ENABLED (Q15-A, regras bloqueantes)
- Property-Based Testing: PARTIAL (Q16-B; regras bloqueantes PBT-02, 03, 07, 08, 09; demais consultivas)

## Fixed Decisions (user)
- **Java version**: 25 (LTS). Overrides "Java 21" in sdd/ documents. Spring Boot version to be set in requirements (3.3 from sdd is superseded).

## Observabilidade e conformidade (decidido nesta sessao)
- **Metricas de negocio**: implementadas (business.vgv, business.vgv.received, business.partner.*, business.first.pass, business.time.to.settle). Avaliacao em `docs/brainstorming/metricas-de-negocio-avaliacao.md`. Fora do contrato atual: Spread/CET e saldo devedor da portabilidade
- **Metricas de aplicacao**: avaliacao em `docs/brainstorming/metricas-de-aplicacao-avaliacao.md`. Saturacao fechada (worker.in.flight / worker.in.flight.max). Pendentes: timer de escrita no DynamoDB, gauges de orcamento por lane, metricas do Tomcat, dependencia micrometer-java21, alertas corrigidos
- **LGPD**: `LogSanitizer` + `PiiRedactingJsonMembersCustomizer` em `platform-starter` mascaram PII em todo o JSON de log dos 4 servicos; handlers nao registram mensagem nem stack trace de excecao. Provado por `PiiNeverReachesTheConsoleTest`
- **Swagger UI**: falha fechada (desligado por padrao); ligado no ambiente local por `scripts/local-up.ps1`
- **Grafana**: CloudWatch como fonte de dados (SQS/DLQ, Lambda, DynamoDB, EMF do Dispatcher); Prometheus para a aplicacao; Pushgateway mantido ate a aplicacao subir; sem Elasticsearch por ora (Jaeger com Badger)

## Riscos registrados (nao resolvidos)
- **Concorrencia x vazao (Lei de Little)**: `worker.max-in-flight` padrao 64 limita a vazao a `64 / latencia`. Para sustentar 180 TPS com latencia de 1 s seriam necessarios 180 em voo. Medir com `worker_in_flight` antes do teste de carga
- **Teste e2e sensivel a tempo**: `WorkerEndToEndTest` falhou uma vez por tempo de parede sob carga da maquina (sem excecao); passa isolado. Tornar a condicao independente de tempo se repetir
- **Correlacao log/trace**: sem exportador OTLP ativo nao ha traceId no log para cruzar com o Jaeger

## Aprovacoes pendentes do workflow
- Code Generation de U1 a U5 (nao houve aprovacao explicita apos a mudanca de escopo)
- Perguntas sem resposta: `no-docker-change-questions.md` Q4-Q6; `topologia-3-ec2-questions.md` T1-T11

## Working Assumptions (reversiveis, sugestoes da IA)
- Sem Docker; emuladores embarcados na JVM (DynamoDBLocal, ElasticMQ); servicos como processos JVM
- Banco relacional em memoria (adaptador de ProposalRepositoryPort); DurableInbox em DynamoDB
- Cifra do payload: envelope AES-GCM; chave local ou KMS com cache de chave de dados
- DoD de idempotencia medido por metricas/contadores; observabilidade por Actuator (scrape), sem stack local
- Terraform, EC2, ALB/WAF, k6 completo e dashboards: depois
