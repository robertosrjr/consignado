# Plano de evolução de SRE — do laboratório à operação

A tabela de baseline registra o início do planejamento. Para o resultado das entregas, consulte o [workflow atual](workflow-plan.md). MFA e aprovação transacional foram excluídos da PoC em 23/10/2026.

Data: 23/10/2026. Status: **planejamento aprovado; S0–S4 aprovadas, execução S5/S6 autorizada**. As tabelas abaixo preservam a proposta inicial; decisões vigentes e adaptações locais estão em `requirements.md`, `workflow-plan.md` e `aidlc-docs/aidlc-state.md`.

Fontes: repositório (código, `docs/sre/`, `aidlc-docs/construction/sre-r0|r1/`, regras em `infrastructure/Configuration/observability/`) e caderno Gemini Notebook "PoC Credito Consignado" (79 fontes; respostas de 23/10/2026).

## 1. Ponto de partida (maturidade atual)

| Dimensão | Nível hoje | Evidência |
|---|---|---|
| SLI/SLO | Contrato v1 e avaliador por experimento; sem SLO contínuo nem error budget | `docs/sre/contrato-medicao.md`, `local-env/.../sre/` |
| Alertas | 7 regras estáticas por sintoma, testadas no promtool | `consignado-sre.yml`; lacuna OPEN/HALF_OPEN reproduzida no L3 |
| Notificação | Alertmanager → SNS comprovado; SQS sem consumidor | `ALERTAS.md` |
| Métricas | Micrometer + EMF; coleta via Pushgateway (paliativo) | `WorkerMetrics`, `EmfMetrics` |
| Tracing | W3C propagado (HTTP, Streams, SQS); 1 jornada com 13 spans comprovada | `HeaderNames`, `StreamRecordMapper`, `SqsLaneQueueAdapter` |
| Logs | JSON local, sem centralização | — |
| Resiliência | Circuit breaker, retry, redrive 3x, cotas; L1 PASS, L2 INCOMPLETE, L3 local | `docs/sre/exercicio-*` |
| Durabilidade | Estado/outbox do worker em memória | `aidlc-state.md` |
| Capacidade | Meta 10k TPS / p95 < 100 ms nunca homologada; L1 9/10 < 100 ms | `validation.md` R1 |
| Infra | Stack única em EC2 (SPOF), Terraform parcial | `infrastructure/` |

## 2. Cruzamento com o caderno

O caderno converge com o diagnóstico do repositório em: SLOs formais por duas jornadas, burn rate multi-janela, fim do Pushgateway, Queue Lag/DLQ, consumidor de alertas com runbooks, Outbox/Sweeper, envelope de carga com fator de replay, matriz de caos e anti-inanição (aging α·Δt).

Divergências — o caderno usa fontes anteriores a 17/10 e deve ser corrigido nestes pontos:

| Afirmação do caderno | Situação no repositório |
|---|---|
| "Tracing fantasma", só pings no Jaeger | Propagação W3C implementada; trace real de 4 serviços/13 spans em 17/10. Lacuna real: cenários de duplicidade/rejeição e correlação de logs. |
| Proposta "morre" entre Inbox e SQS sem mecanismo | Dispatcher já publica a partir de DynamoDB Streams; falta Sweeper de reconciliação para RECEIVED órfão e prova sob falha. |
| PagerDuty / Aurora / ECS multi-AZ como passo imediato | Restrições do projeto: custo (sem NAT, destroy após testes), sem Docker local, banco em memória na fase 1, métricas neutras (Datadog talvez). Tratar como fase final opcional. |

## 3. Objetivo do incremento

Passar de "experimentos que provam" para "operação que mede continuamente e reage": SLO com orçamento de erro, alerta pela queima do orçamento, ciclo de incidente fechado e capacidade conhecida — mantendo o invariante de correção (zero perda, zero duplicidade financeira), que não é negociável pelo error budget.

## 4. Roadmap proposto (ondas)

| Onda | Tema | Entregas | Critério de conclusão (DoD) |
|---|---|---|---|
| **S0** | Fechar o legado da PoC | Concluir L2 offline; corrigir detector OPEN/HALF_OPEN (ex.: `max_over_time`/razão de tempo não-CLOSED); remover regras `pedidos-api` | promtool cobre alternância e dispara; L2 PASS/FAIL declarado |
| **S1** | SLOs formais | Documento de SLO: J1 aceite durável 99,9%/30d, latência p95/p99; J2 decisão no prazo por coorte (fast 95% < 2 min, standard 90% < 45 min), contenção ≤ 180 TPS, Queue Lag; política de error budget | SLOs aprovados pelo usuário, com população, janela e exclusões |
| **S2** | Medição contínua | Recording rules de SLI; avaliador de prazos durável (índice `status#due`); métricas Oldest Message Age e DLQ; scrape direto substituindo Pushgateway onde alcançável | Dashboard de SLO e budget restante alimentado sem experimento manual |
| **S3** | Alertas por burn rate | Regras MWMB (14,4x 5m/1h página; 6x 30m/6h ticket) para J1 e J2; alertas de DLQ e frescor do observador; testes promtool | Testes promtool verdes; alertas estáticos redundantes desligados |
| **S4** | Resposta a incidentes | Consumidor da SQS de alertas (dedup por episódio, link a runbook/dashboard/trace); runbook por alerta; template de postmortem blameless; 1 game day | Alerta sintético chega ao destino final; game day com postmortem registrado |
| **S5** | Durabilidade e dados | Outbox/estado do worker persistidos; Sweeper de RECEIVED órfão; caos: timeout pós-escrita, partição Inbox→SQS, queda do worker | Reinício não perde nem duplica; cenários do caderno comprovados |
| **S6** | Capacidade | Envelope de carga com fator de replay; ensaio progressivo com relatório conciliado; modelo de drenagem; aging α·Δt se starvation comprovado | Limite sustentável medido e publicado; SLO J2 ajustado à física de 180 TPS |
| **S7** | Plataforma (opcional) | Observabilidade como código (Terraform de regras/dashboards); stack fora do SPOF; logs centralizados; caos agendado em CI | Ambiente recriável; custo aprovado |

Ordem sugerida: S0 → S1 → S2 → S3 → S4, com S5 em paralelo a S3/S4. S6 depende de S2 e S5; S7 por último.

## 5. Princípios

- Ausência de dado não é sucesso; INCOMPLETE é resultado válido.
- Labels só categóricas (service, env, lane, outcome); sem CPF, chaves ou IDs (LGPD).
- Métricas neutras via Micrometer; nenhuma dependência de fornecedor.
- Custo: ambiente AWS ligado só durante ensaios; sem NAT Gateway.
- Cada onda com evidência reproduzível e registro em `aidlc-docs/construction/`.

## 6. Próximo passo

Planejamento aprovado e executado até a validação da S5. Os ensaios da S6 foram entregues, mas a homologação permanece pendente: p99 no servidor ≈ 245 ms para alvo de 200 ms. Continuar pela investigação de desempenho e correção do pacing do gerador antes de repetir a qualificação. Não reiniciar o levantamento de requisitos nem as etapas já aprovadas.

Situação consolidada em `workflow-plan.md` e `aidlc-docs/aidlc-state.md`; evidências em `aidlc-docs/construction/sre-s6/validation.md` e `docs/sre/capacidade-local.md`. Logs de build organizados em `logs/` e ignorados pelo Git. Ambiente local encerrado; AWS permanece fora da execução.
