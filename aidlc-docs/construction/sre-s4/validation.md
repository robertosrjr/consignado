# S4 — Resposta a incidentes: validação

Data: 23/10/2026. Local, sem AWS. Opção A (ElasticMQ como SQS local) aprovada.

## Entregas

- `local-env`, pacote `incident`:
  - `AlertWebhookBridge`: `POST /alerts` → envelope SNS → `sqs-alerts`; limite de 256 KB, 400 para JSON inválido, 503 se a fila falhar;
  - `IncidentQueueConsumer`: apaga a mensagem só após publicar; mensagem veneno é apagada e registrada;
  - `IncidentProcessor`: dedup por `fingerprint@startsAt`, allowlist de labels, reconstrução a partir do histórico;
  - `IncidentNotifier` / `FileIncidentNotifier`: JSONL.
- `LocalSqs.createAlertQueue`; `LocalEnvMain` sobe a ponte (porta `LOCAL_ENV_ALERT_WEBHOOK_PORT`, padrão 9096) e o consumidor (`LOCAL_ENV_INCIDENTS_FILE`).
- `alertmanager.local.yml` (rota `notification=sre` → ponte; sem SNS nem Function URL). O `alertmanager.yml` de produção não foi alterado.
- Runbooks por alerta (já na S3); template `docs/sre/postmortem-template.md`; postmortem do game day `docs/sre/postmortem-gameday-s4-20261023.md`.

## Evidência

- `mvn -o verify -pl local-env -am`: BUILD SUCCESS; `IncidentProcessorTest` (7) e `IncidentFlowIT` (1, ElasticMQ real).
- amtool `check-config` com SUCCESS em `alertmanager.local.yml`.
- Game day 23/10 21:02 UTC: um episódio `opened` (11 s) e `resolved` (~49 s), com o reenvio deduplicado. Artefatos em `applications/.local/gameday-s4-20261023/`. Processos encerrados e portas livres ao final.

## Limites

- E-mail não implementado nem exercitado (sem SMTP local); a interface permite incluí-lo.
- O game day usou alerta sintético; Prometheus avaliando as regras reais fica para S5/S6.
- O consumidor SQS de produção (SNS → SQS na AWS) não foi conectado.
