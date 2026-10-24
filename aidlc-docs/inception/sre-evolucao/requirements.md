# Requisitos — evolução de SRE (S0–S6)

Data: 23/10/2026. Status: **aprovado** (ver `aidlc-docs/audit.md`). Base: `plano-evolucao-sre.md` e respostas Q1–Q5.

## Decisões

| # | Decisão |
|---|---|
| Q1 | Escopo S0–S6; S7 (plataforma gerenciada) fora deste incremento |
| Q2 | SLO inicial folgado: J1 99,5%/30d; J2 fast 95% < 2 min, standard 90% < 45 min; revisão após S2/S6 |
| Q3 | Notificação: consumidor da SQS de alertas grava incidente em arquivo local + e-mail, atrás de interface `IncidentNotifier` |
| Q4 | Estado/outbox do worker em DynamoDB (Local embarcado, sem Docker) |
| Q5 | Tudo local: Prometheus/Grafana como processos na máquina; nada na AWS neste incremento (decisão do usuário, 23/10) |

## Requisitos funcionais

- **RF-01 (S0)** Concluir L2 offline com resultado PASS/FAIL/INCOMPLETE declarado.
- **RF-02 (S0)** Detector de circuito não-CLOSED cobre alternância OPEN/HALF_OPEN; promtool reproduz e dispara.
- **RF-03 (S0)** Remover regras herdadas `pedidos-api`.
- **RF-04 (S1)** Documento de SLO com população, janela, exclusões e política de error budget para J1 e J2.
- **RF-05 (S2)** Recording rules de SLI; avaliador de prazos durável (`status#due`); métricas Oldest Message Age e DLQ; scrape direto onde alcançável.
- **RF-06 (S2)** Dashboard de SLO e budget restante sem experimento manual.
- **RF-07 (S3)** Alertas MWMB (14,4x 5m/1h página; 6x 30m/6h ticket), DLQ e frescor do observador, com testes promtool; estáticos redundantes desligados.
- **RF-08 (S4)** Consumidor SQS com dedup por episódio e links para runbook/dashboard/trace; runbook por alerta; template de postmortem; 1 game day.
- **RF-09 (S5)** Outbox/estado do worker em DynamoDB; Sweeper de RECEIVED órfão; caos: timeout pós-escrita, partição Inbox→SQS, queda do worker.
- **RF-10 (S6)** Envelope de carga com fator de replay; ensaio progressivo conciliado; modelo de drenagem; aging α·Δt só se starvation comprovado.

## Requisitos não funcionais

- **RNF-01** Invariante de correção (zero perda, zero duplicidade financeira) fora do error budget.
- **RNF-02** Labels apenas categóricas; nenhum CPF, chave ou ID em telemetria (LGPD).
- **RNF-03** Métricas neutras via Micrometer.
- **RNF-04** Nenhum recurso AWS neste incremento; tudo local.
- **RNF-05** Sem Docker local; emuladores embarcados na JVM.
- **RNF-06** Java 25; gates existentes (cobertura 80%, Spotless, Enforcer, ArchUnit) mantidos.
- **RNF-07** Ausência de dado não é sucesso; INCOMPLETE é resultado válido.
