# S4 — Functional Design: resposta a incidentes (local)

Data: 23/10/2026. Restrição: nada na AWS. Decisão Q3: incidente em arquivo + e-mail, atrás de interface.

## Caminho do alerta

Em produção: Alertmanager → SNS → SQS → consumidor. Localmente não há SNS. Opções:

| | A — ElasticMQ como SQS local (recomendada) | B — webhook direto |
|---|---|---|
| Caminho | Alertmanager → webhook de uma ponte local → fila `sqs-alerts` no ElasticMQ → consumidor SQS | Alertmanager → webhook do consumidor |
| Fidelidade ao alvo | O consumidor é o mesmo código que leria a SQS real (só troca o endpoint) | Consumidor HTTP diferente do de produção |
| Peças | Ponte webhook→SQS + consumidor | Só consumidor |

## Consumidor de incidentes (comum às opções)

- Módulo `local-env`, pacote `incident` (ferramenta de desenvolvimento, sem novo deployable).
- **Episódio** = `fingerprint` do alerta + `startsAt`. `firing` repetido do mesmo episódio não abre outro incidente; `resolved` fecha o episódio.
- Cada incidente traz alerta, severidade, SLO/lane, início e fim, mais links para o runbook (`ALERTAS.md#...`), o dashboard (`dashboard_path`) e a busca no Jaeger (`jaeger_service`/`operation`). Sem payload de negócio nem identificadores: só os labels categóricos do alerta (LGPD).
- Saída por `IncidentNotifier`:
  - `FileIncidentNotifier`: JSONL em `applications/.local/incidents/incidents.jsonl`;
  - `SmtpIncidentNotifier`: desligado por padrão, liga só com host SMTP configurado; sem credenciais versionadas.
- Dedup em memória e reconstruída a partir do JSONL na partida, para não reabrir episódios após reinício.

## Demais entregas

- Template de postmortem blameless: `docs/sre/postmortem-template.md`.
- Game day local, com Prometheus 3.5.0 e Alertmanager 0.28.1 como processos (binários já baixados):
  1. alerta sintético via `amtool alert add`, com resolução;
  2. conferência de um episódio único no JSONL, aberto e fechado;
  3. postmortem preenchido do exercício.
  O DoD da S4 é o alerta sintético chegar ao destino final.
- Rota local `alertmanager.local.yml`, sem SNS e sem a Function URL; o `alertmanager.yml` de produção fica intocado.
