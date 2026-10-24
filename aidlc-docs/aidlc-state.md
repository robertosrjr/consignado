# AI-DLC State Tracking

## Project Information
- **Project Name**: Platform Engine - PoC Credito Consignado (Ingestao Massiva e Resiliencia)
- **Project Type**: Brownfield (origem greenfield; incremento SRE em 17/10/2026)
- **Start Date**: 2026-10-07T12:51:04Z
- **Current Stage**: CONSTRUCTION — incremento evolucao SRE de 23/10/2026, S5 validada; S6 com ensaios e relatorio entregues, homologacao pendente por p99. Ambiente local encerrado, sem AWS. Retomada em `construction/sre-s6/validation.md`. A PoC anterior foi encerrada em 18/10/2026.
- **Last Validation**: 23/10/2026 - build completo com integracao, 10 modulos e 473 testes sem falhas/erros/ignorados; S5 com reinicio real do worker e seis propostas/seis averbacoes unicas. S6 com quatro rodadas encerradas e homologacao pendente por p99; falhas preservadas. Log do build em `logs/s5-final.log`; evidencias em `construction/sre-s5/validation.md`, `construction/sre-s6/validation.md` e `.local/` de applications. Historico L1 de 18/10 em `construction/sre-r1/validation.md`.
- **R0 Validation**: 17/10/2026 - `mvn -pl mock-legacy -am verify`: BUILD SUCCESS, parent + 4 modulos, 139 testes, sem falhas/erros, gates de formatacao e cobertura aprovados. Nao e build completo da aplicacao. Evidencia em `construction/sre-r0/validation.md`.
- **Last Build (historico)**: 2026-10-09 - `mvn verify` BUILD SUCCESS, 9 modulos, 356 testes, 0 falhas; gates de cobertura (80%), Spotless, Enforcer e ArchUnit aprovados. Nao representa validacao do codigo atual; nenhum build executado no incremento documental.

## Workspace State
- **Existing Code**: Yes
- **Reverse Engineering Needed**: Incremental, concluida para o planejamento SRE; ver `inception/sre-resilience/reverse-engineering/`.
- **Workspace Root**: C:\Developer\Workspace\java\consignado
- **Source Documents**: `inception/sre-resilience/`, `docs/sre/`, codigo e configuracao existentes. Fontes iniciais historicas: `sdd/` (SDD, Especificacao AI-DLC, Blueprint, 2 narrativas).

## Code Location Rules
- **Application Code**: Workspace root (`applications/`), NEVER in `aidlc-docs/`
- **Workflow Documentation**: `aidlc-docs/`; guias operacionais existentes permanecem em `docs/`, sem duplicar o plano.
- **Rule details**: `.aidlc-lite-rule-details/`

## Incremento SRE - 17/10/2026

- **Plano**: [falha, deteccao e recuperacao](inception/sre-resilience/plans/execution-plan.md).
- **Encerramento**: PoC de SRE/observabilidade concluida por decisao do usuario em 18/10/2026. L1 e exercicio real do worker forneceram evidencia integrada; L2 permaneceu INCOMPLETE na conciliacao final; L3 comprovou protecao local e uma lacuna do detector. R2-R4 e demais endurecimentos foram movidos para backlog, sem ampliar as alegacoes da PoC.
- **Pedido inicial**: elaborar plano utilizando AI-DLC. Continuidade posterior: "seguimos o plano do ia-dlc então"; iniciar R0 local, mantendo EC2 desligada.
- [x] Workspace Detection: brownfield.
- [x] Reverse Engineering: sistema e interfaces, escopo incremental.
- [x] Requirements Analysis: RF-01 a RF-09 e RNF-01 a RNF-06.
- [x] Workflow Planning: profundidade Standard; continuidade autorizada na mensagem posterior do usuario.
- User Stories dispensadas; desenho e unidade R0 documentados em `construction/sre-r0/unit-design.md` antes do codigo. Demais unidades serao detalhadas quando iniciadas.
- [x] Construction encerrada no escopo delimitado da PoC; R2-R4 foram reclassificados como backlog.
  - [x] R0: controle local, TTL, restauracao e manifest implementados; testes locais verdes. Preflight integrado realizado em 18/10 para L1.
  - [x] R1/L1: preflight, coorte JMeter e recuperacao real; relato em `docs/sre/exercicio-falha-legado-l1.md`.
  - [x] R1/L2 executado com conciliacao final incompleta; L3 validado localmente, com lacuna do detector registrada.
- [x] Validacao operacional encerrada com limites explícitos; itens sem evidência permanecem no backlog.
- **Estado operacional (18/10, 12:19 America/Sao_Paulo)**: quatro processos locais encerrados, containers parados, Docker/socket/containerd inativos; EC2 `i-082b5f183d18a8d25` confirmada `stopped` por waiter e describe. Volume EBS e arquivos de evidencias preservados. Artefatos em `.local/shutdown-local-20261018.json` e `.local/shutdown-ec2-20261018.json` dentro de applications.
- **Baseline atual**: seis dashboards versionados, sete regras SRE, tracing com jornadas documentadas e exercicio real de pausa do worker. Notificacoes verificadas ate SNS; consumo na SQS cloud nao comprovado. Ver `docs/sre/README.md` e `docs/sre/exercicio-falha-worker.md`.
- **Riscos prioritarios**: alerta de legado OPEN por 2 min versus sondagem apos 10 s; redrive apos 3 recebimentos; estado/chaves locais em memoria. O plano propoe validacao, nao declara esses riscos resolvidos.
- **Decisoes preservadas**: sem persistencia duravel do worker por ora; Terraform fora deste incremento; Business Fingerprint v2 desligado; sem novos dashboards permanentes.
- **Restricao operacional do usuario (17/10/2026, America/Sao_Paulo)**: "não ligue mais a ec2 hoje". Nao iniciar a instancia nesta data; continuar somente trabalho local.
- **Nova autorizacao (18/10/2026)**: "pode ligar o ambiente e vamos testar" / "vamos testar". EC2 e stack iniciadas, aplicacoes locais ativas; IP observado `18.228.193.162`, endpoints efetivos atualizados em `.local/observability.json`. Estado anterior desligado e restricao de 17/10 sao historicos.
- **Pausa posterior (18/10/2026)**: "vaoms pausar os testes e deixar para depois". Sem novas coortes. Falha da tentativa L2 restaurada; ambiente mantido ligado. Artefatos L2 em `applications/.local/legacy-l2-20261018-01/` e `02/`; trace da tentativa02 `5cf549257710db75df51aac27a25df16`. Harness bruto02 INCOMPLETE por ExitCode nulo no Windows, apesar de JMeter concluir; preservar resultado original e concluir verificacao das fontes antes de marcar L2 completo.
- **Encerramento posterior autorizado**: "pode desligar os ambinetes e a EC2 para evitar gastos". Login AWS renovado pelo usuario; parada concluida e verificada em 18/10/2026 12:19. Testes continuam pausados. Na retomada, iniciar ambiente conscientemente, obter IP atual e conferir os artefatos L2 ja coletados antes de qualquer reenvio.

## Escopo confirmado em 23/10/2026

MFA, step-up e aprovação transacional ficam fora da PoC por decisão do usuário. Os documentos em `docs/brainstorming/MFA/` permanecem como estudos. Continuar a S6 sem ampliar o escopo.

## Stage Progress - historico da entrega inicial

As secoes abaixo preservam o registro anterior. Pendencias, contagens e afirmacoes de observabilidade pertencem ao momento em que foram registradas; nao substituem a baseline de 17/10 acima nem revogam aprovacoes anteriores.
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
  - [ ] U7 load-tests-and-dashboards: PARCIAL. Fonte CloudWatch provisionada no Grafana + politica IAM + mapa de metricas. 2026-10-16: 3 paineis JSON (applications/observability/grafana/dashboards) provisionados no Grafana do EC2; envio ao Pushgateway (falha fechada, `local-up.ps1 -PushgatewayAddress`). Faltam: rodada de carga com evidencia, regras de alerta definitivas (as do EC2 ainda sao de pedidos-api)
- [ ] Build and Test: PARCIAL. `mvn verify` verde; ITs do ingestion-api rodam com `-Pintegration` (DynamoDB Local embarcado). `scripts/local-up.ps1` concluiu a subida completa em 2026-10-09 e o fluxo foi verificado manualmente. Faltam: ITs do worker e teste de carga com evidencia

### OPERATIONS PHASE
- [ ] Operations (PLACEHOLDER)

## Extension Configuration
- Security Baseline: ENABLED (Q15-A, regras bloqueantes)
- Property-Based Testing: PARTIAL (Q16-B; regras bloqueantes PBT-02, 03, 07, 08, 09; demais consultivas)

## Fixed Decisions (user)
- **Java version**: 25 (LTS). Overrides "Java 21" in sdd/ documents. Spring Boot version to be set in requirements (3.3 from sdd is superseded).

## Observabilidade e conformidade (decidido nesta sessao)
- **Metricas de negocio**: implementadas (business.vgv, business.vgv.received, business.partner.*, business.first.pass, business.time.to.settle). Avaliacao em `docs/brainstorming/Fase-01/metricas-de-negocio-avaliacao.md`. Fora do contrato atual: Spread/CET e saldo devedor da portabilidade
- **Metricas de aplicacao**: avaliacao em `docs/brainstorming/Fase-01/metricas-de-aplicacao-avaliacao.md`. Saturacao fechada (worker.in.flight / worker.in.flight.max). Pendentes: timer de escrita no DynamoDB, gauges de orcamento por lane, metricas do Tomcat, dependencia micrometer-java21, alertas corrigidos
- **LGPD**: `LogSanitizer` + `PiiRedactingJsonMembersCustomizer` em `platform-starter` mascaram PII em todo o JSON de log dos 4 servicos; handlers nao registram mensagem nem stack trace de excecao. Provado por `PiiNeverReachesTheConsoleTest`
- **Swagger UI**: falha fechada (desligado por padrao); ligado no ambiente local por `scripts/local-up.ps1`
- **Grafana**: CloudWatch como fonte de dados (SQS/DLQ, Lambda, DynamoDB, EMF do Dispatcher); Prometheus para a aplicacao; Pushgateway mantido ate a aplicacao subir; sem Elasticsearch por ora (Jaeger com Badger)

## Riscos registrados (nao resolvidos)
- **Concorrencia x vazao (Lei de Little)**: RESOLVIDO em 2026-10-09. `worker.max-in-flight` padrao 360 (180 TPS x 2 s do slow-call); pool HTTP do DynamoDB dimensionado (ingestion 200, worker = max-in-flight); ZGC + heap fixo na ingestion-api. Subir a concorrencia expos bug real (circuito aberto levava propostas validas a DLQ), corrigido com back-pressure (`LegacyAvailabilityPort`). Medir com `worker_in_flight` no teste de carga
- **Teste e2e sensivel a tempo**: RESOLVIDO em 2026-10-09. A causa era o circuito aberto levando mensagens a DLQ (corrigido com back-pressure). Resta uma janela de corrida conhecida: o circuito pode abrir entre o despacho e o envio, e a recusa local conta como tentativa interna (sem chamada ao legado). Tratamento previsto na M2 (distincao "antes do envio")
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

## Incremento evolucao de SRE - 23/10/2026
- [x] Workspace Detection (brownfield, artefatos SRE existentes)
- [x] Requirements Analysis aprovada (`inception/sre-evolucao/requirements.md`)
- [x] Workflow Planning aprovado (`inception/sre-evolucao/workflow-plan.md`)
- User Stories: SKIPPED
- Escopo: S0-S6; S7 fora
- Restricao do usuario (23/10/2026): "não vamos levar nada para AWS agora, vamos fazer tudo local" — Q5 passa a C (processos locais); EC2 nao sera ligada
- [x] Unidade S0 aprovada (`construction/sre-s0/validation.md`)
- [x] S1 SLOs formais aprovada (`construction/sre-s1/summary.md`)
- [x] S2 Medicao continua aprovada (`construction/sre-s2/validation.md`)
- [x] S3 Alertas por burn rate aprovada (`construction/sre-s3/validation.md`)
- [x] S4 Resposta a incidentes aprovada (`construction/sre-s4/validation.md`)
- Pausa de 23/10 encerrada; S5 e S6 autorizadas ("pode seguir S4, S5, S6")
- [x] S5 Durabilidade validada (`construction/sre-s5/validation.md`): build completo com 473 testes e reinicio real do worker, sem nova averbacao para replays terminais.
- [ ] S6 Capacidade — ensaios e relatorio entregues (`construction/sre-s6/validation.md`, `docs/sre/capacidade-local.md`); DoD de homologacao nao atingido. A 1 req/s, servidor p95 ~89 ms e p99 ~245 ms: criterio de p99 <= 200 ms reprovado. Proximo passo: isolar custo do aceite/host e corrigir pacing do gerador antes de repetir qualificacao. Processos locais encerrados; AWS nao acessada.
- [x] Organizacao dos logs: sete logs de build movidos de `applications/` para `logs/`; `.gitignore` cobre `/logs/`, `*.log` e `*.log.*`. Nenhum log rastreado pelo Git; referencia do build atualizada.
- **Retomada**: continuar a investigacao de desempenho e a homologacao da S6. S0-S5 nao precisam ser reiniciadas; S7 segue fora do escopo. Nenhum ambiente em execucao.
