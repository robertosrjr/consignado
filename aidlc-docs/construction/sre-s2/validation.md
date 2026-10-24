# S2 — Medição contínua: validação

Data: 23/10/2026. Execução local, sem AWS. Desenho: `functional-design.md`; opção A aprovada.

## Entregas

| Item | Onde |
|---|---|
| `sloOpen` gravado no roteamento | `DynamoInboxStatusUpdater` (dispatcher) |
| GSI esparso `sloOpen-createdAt` (projeta `decision`, `decidedAt`) | `LocalDynamoDb.createInboxTable` |
| Decisão gravada uma vez (`decision`, `decidedAt`) | `DecisionRecorderPort` + `DynamoDbDeadlineAdapter`; chamada em `ProcessProposalService` |
| Avaliador de prazos, `slo.j2.events{lane,outcome}` | `DeadlineEvaluator` (`worker.slo.*`, padrão 2m/45m, ciclo 15 s) |
| Buckets 2 min e 45 min | `business.time.to.settle` |
| SLO buckets 100/200 ms | `http.server.requests` na ingestion-api |
| `worker.dlq.depth` (NaN sem URL) | `QueueDepthSampler`; `WORKER_QUEUE_DLQ_URL` em `local-up.ps1` |
| Recording rules J1/J2 + budget 30d | `rules/consignado-slo.yml` (18 regras) |
| Dashboard | `observability/grafana/dashboards/slo-error-budget.json` |

## Evidência

- `mvn -o verify` nos módulos shared, telemetry, starter, platform-aws, ingestion-api, dispatcher, worker e local-env: BUILD SUCCESS, com gates (Spotless, cobertura, Enforcer, ArchUnit) e ITs do local-env, inclusive `DispatcherEndToEndIT` com a tabela nova.
- `mvn -o verify -Pintegration -pl worker -am`: BUILD SUCCESS. Worker com 144 testes; `DeadlineEvaluatorTest` (4), `ProcessProposalServiceTest` (10, inclusive a decisão gravada uma vez) e `DynamoDbDeadlineAdapterIT` (4, DynamoDB Local real: índice esparso, corte, decisão única, fechamento condicional).
- promtool 3.5.0: `check rules` em `consignado-slo.yml` com 18 regras; `test rules` nos três arquivos com SUCCESS, inclusive "sem tráfego = sem dado".

## Limites

- A pilha Prometheus/Grafana local não foi executada: regras e dashboard foram validados por promtool e JSON, sem dados reais.
- Itens antigos sem `sloOpen` (anteriores à mudança) ficam fora do J2. Propostas nunca roteadas (`unrouted`) não entram no índice.
- Oldest Message Age não implementado: a API SQS não expõe esse atributo; fica como proxy via `worker.queue.depth` e tempo de drenagem.
- Scrape direto no lugar do Pushgateway e contador de conciliação 202 ↔ Inbox adiados (S5/pilha local).
- J1 exclui todo 429, porque a métrica não distingue cota do cliente de recusa indevida.
- Terraform da tabela AWS não atualizado (fora do escopo local).
