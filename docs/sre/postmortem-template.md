# Postmortem — <título curto do incidente>

**Formato blameless.** O objetivo é entender o sistema e melhorar as defesas, não apontar culpados. Descreva decisões pelo que se sabia no momento.

| Campo | Valor |
|---|---|
| Incidente (episódio) | `<fingerprint@startsAt>` do registro em `applications/.local/incidents/incidents.jsonl` |
| Alerta(s) | ex.: `ConsignadoJ2DeadlineBurnFast` (lane fast) |
| Severidade | critical / warning |
| Início, detecção, mitigação, fim | horários em America/Sao_Paulo |
| Jornadas e SLO afetados | J1 / J2 (lane); link para `docs/sre/slo.md` |
| Budget consumido | antes → depois (painel "SLO e Error Budget") |
| Invariante financeiro | Perda ou duplicidade? sim/não, com a evidência (conciliação, ledger) |
| Autor e revisores | |

## Resumo

Duas a três frases: o que o usuário sentiu, por quanto tempo e como terminou.

## Impacto

- Propostas afetadas: quantidade e valor agregado, nunca CPF, chave ou ID individual.
- Fora do prazo / sem decisão / na DLQ.
- Ação do usuário necessária (ex.: reenvio com a mesma Idempotency-Key)?

## Linha do tempo

| Horário | Evento | Fonte (alerta, trace, log, painel) |
|---|---|---|
| | | |

## Detecção

- O alerta certo disparou? Com que atraso em relação ao início real? (J2 mede no vencimento.)
- Houve ruído, alerta faltante ou inibição indevida?

## Causa e fatores contribuintes

Mecanismo técnico, condições que permitiram o problema e por que as defesas (retry, circuit breaker, cotas, redrive) não contiveram antes.

## O que funcionou / o que atrapalhou

## Ações

| Ação | Tipo (prevenir / detectar / mitigar) | Responsável | Prazo | Status |
|---|---|---|---|---|
| | | | | |

## Política de error budget aplicada

Faixa do budget restante e decisão tomada (ver `docs/sre/slo.md`, "Política de error budget").

## Evidências

Links para o registro do incidente, traces Jaeger, painéis e artefatos em `applications/.local/`. Não colar payloads nem dados pessoais (LGPD).
