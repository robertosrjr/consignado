# S3 — Alertas por burn rate: validação

Data: 23/10/2026. Execução local, sem AWS (RF-07).

## Entregas

- `rules/consignado-slo-alerts.yml`: 8 alertas.
  - Burn rate multi-janela (página 14,4x em 5m+1h, `for` 2m; ticket 6x em 30m+6h, `for` 15m) para J1 disponibilidade, J1 latência e J2 por lane.
  - `ConsignadoDlqNotEmpty` e `ConsignadoSloEvaluatorStale`.
- `consignado-slo.yml`: janelas 30m e 6h de latência J1 (20 regras).
- Worker: gauge `slo.j2.last.evaluation` (0 até o primeiro ciclo, para o alerta de frescor tratar "nunca rodou" como antigo).
- Removidos `ConsignadoIngestionErrors` e `ConsignadoIngestionLatency`, com seus dois cenários de disparo. A inibição do Alertmanager passou a ser BurnFast suprimindo BurnSlow (`equal: [slo, lane]`), também no exemplo de e-mail.
- Runbooks em `ALERTAS.md`; tabelas em `docs/sre/README.md` e `docs/sre/alertas/README.md`.

## Evidência

- promtool 3.5.0: `check rules` nos 3 arquivos com SUCCESS; `test rules` nos 4 arquivos de teste com SUCCESS. Novos cenários: página e depois ticket, erro dentro do budget, pico encerrado resolvendo pela janela curta, latência, J2 por lane, DLQ e avaliador parado ou em dia.
- amtool 0.28.1: `check-config` com SUCCESS em `alertmanager.yml` e `alertmanager.email.example.yml`.
- `mvn -o verify -pl worker -am`: BUILD SUCCESS, worker com 145 testes.

## Limites

- Na lane standard (budget 10%) a página rápida é impossível (14,4 × 10% > 100%); a cobertura vem do ticket, DLQ, WorkerStalled e LegacyUnavailable.
- J2 detecta no vencimento: atraso inerente de até um prazo.
- Nenhum alerta foi carregado em Prometheus real nem notificado; o destino final é a S4.
- `amtool` baixado em `C:\Developer\Programas\alertmanager-0.28.1.windows-amd64`.
