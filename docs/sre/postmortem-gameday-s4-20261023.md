# Postmortem — Game day S4: alerta sintético de DLQ até o registro de incidentes

**Formato blameless.** Exercício planejado; nenhum usuário afetado.

| Campo | Valor |
|---|---|
| Incidente (episódio) | `e11873081d469ebe@2026-10-23T21:02:15Z` |
| Alerta | `ConsignadoDlqNotEmpty` (sintético, via `amtool alert add`) |
| Severidade | critical |
| Início / detecção / fim | 18:02:15 / 18:02:26 (registro aberto) / resolvido 18:02:36, registrado 18:03:25 (America/Sao_Paulo) |
| Jornadas e SLO afetados | Nenhum (exercício) |
| Invariante financeiro | Não aplicável; sem tráfego de propostas |

## Resumo

Exercício para provar o DoD da S4: um alerta deve chegar ao destino final. O caminho local Alertmanager 0.28.1 → ponte webhook → fila `sqs-alerts` (ElasticMQ) → consumidor → `incidents.jsonl` funcionou ponta a ponta, com um episódio aberto e fechado.

## Linha do tempo (UTC)

| Horário | Evento | Fonte |
|---|---|---|
| 21:02:15 | Alerta firing injetado | `amtool` |
| 21:02:26 | Incidente `opened` gravado (group_wait 10 s) | `incidents.jsonl` |
| ~21:02:35 | Mesmo alerta reenviado (mesmo `startsAt`) | `amtool` |
| 21:02:36 | Alerta encerrado (`--end`) | `amtool` |
| 21:03:25 | Incidente `resolved` gravado (group_interval 1 min) | `incidents.jsonl` |

## Detecção

- Tempo até o registro: 11 s, coerente com `group_wait: 10s`.
- O reenvio do mesmo episódio não abriu outro incidente: dedup por `fingerprint@startsAt`.
- O resolved levou cerca de 49 s, porque o Alertmanager notifica no próximo `group_interval`.

## O que atrapalhou

- O `dashboardPath` saiu como `D:/consignado-slo-budget/...`: o Git Bash (MSYS) converteu o argumento `/d/...` do `amtool` em caminho Windows. É defeito do script do exercício, não do consumidor; alertas reais vêm do Prometheus, sem essa conversão.
- O e-mail não foi exercitado: não há servidor SMTP local (sem Docker). O destino em arquivo cumpre o DoD; o e-mail fica atrás de `IncidentNotifier`.

## Ações

| Ação | Tipo | Responsável | Status |
|---|---|---|---|
| Em scripts de game day no Git Bash, exportar `MSYS_NO_PATHCONV=1` antes do `amtool` | prevenir | SRE | aberta |
| Implementar `SmtpIncidentNotifier` quando houver relay SMTP local | detectar | SRE | aberta |
| Exercitar a cadeia com Prometheus avaliando as regras reais (não só `amtool`) | detectar | SRE | aberta (S5/S6) |

## Evidências

`applications/.local/gameday-s4-20261023/`: `incidents.jsonl`, `alertmanager.log`, `local-env.log`, `gameday.sh`.
