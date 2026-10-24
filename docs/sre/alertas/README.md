# Alertas da PoC de consignado

Revisão: 23/10/2026. As condições exatas, comandos e runbooks ficam no [manual técnico](../../../infrastructure/Configuration/observability/ALERTAS.md). Este guia resume o fluxo e as evidências.

## Rotas de notificação

| Ambiente | Caminho | O que foi comprovado |
|---|---|---|
| Local, incremento S4 | Alertmanager → webhook em `127.0.0.1:9096/alerts` → ElasticMQ `sqs-alerts` → consumidor → JSONL | Game day sintético com abertura, resolução e deduplicação do episódio |
| AWS, primeiro ciclo | Alertmanager → SNS → SQS | Publicação até SNS nos exercícios de 17/10; entrega SQS registrada em ensaio anterior |

A configuração local é [alertmanager.local.yml](../../../infrastructure/Configuration/observability/config/alertmanager/alertmanager.local.yml). A rota AWS permanece em [alertmanager.yml](../../../infrastructure/Configuration/observability/config/alertmanager/alertmanager.yml). Não há Lambda na rota SNS → SQS. O consumidor local não foi conectado à fila AWS.

O consumidor local grava `.local/incidents/incidents.jsonl`, relativo ao diretório de execução, ou o caminho de `LOCAL_ENV_INCIDENTS_FILE`. Isso demonstra processamento do episódio, sem comprovar atendimento humano ou envio de e-mail.

## Alertas versionados

| Família | Sinal | Primeira investigação |
|---|---|---|
| `ConsignadoScrapeDown`, `ConsignadoPushMissing` | Coleta indisponível ou push ausente/antigo | Processo, `/targets`, rede e exportador |
| `ConsignadoJ1AvailabilityBurnFast/Slow` | Consumo do orçamento de disponibilidade do aceite | Timeouts, escrita na Inbox e erros 5xx |
| `ConsignadoJ1LatencyBurnFast/Slow` | Consumo do orçamento de aceites acima de 100 ms | Banco, aquecimento e saturação |
| `ConsignadoJ2DeadlineBurnFast/Slow` | Decisões fora do prazo | Inbox, filas, worker e legado |
| `ConsignadoWorkerStalled`, `ConsignadoBacklogDelayed` | Fila sem saída ou drenagem elevada | Consumo, entrada e orçamento de vazão |
| `ConsignadoLegacyUnavailable` | Circuito fora de CLOSED, com OPEN recente e fila pendente | Falhas e latência do legado |
| `ConsignadoDlqNotEmpty` | Mensagens na DLQ | Motivo de falha antes de qualquer reenvio |
| `ConsignadoSloEvaluatorStale` | Avaliador J2 sem ciclo recente, com push ativo do worker | DynamoDB e logs do avaliador |

As regras estáticas antigas de erro e latência foram substituídas na S3. Na lane standard, a queima rápida de 14,4 vezes o orçamento de 10% é inalcançável; a regra lenta e os sinais de causa continuam relevantes.

As regras estão em [consignado-sre.yml](../../../infrastructure/Configuration/observability/config/prometheus/rules/consignado-sre.yml) e [consignado-slo-alerts.yml](../../../infrastructure/Configuration/observability/config/prometheus/rules/consignado-slo-alerts.yml). A definição de população e metas está nos [SLOs](../slo.md).

## Limites de cobertura

- Regras dependentes de `push_time_seconds` não oferecem a mesma cobertura com scrape direto. Isso inclui o alerta de frescor do avaliador; o ensaio local não resolve essa lacuna.
- O J2 não cobre itens nunca roteados ou anteriores ao índice `sloOpen`. Ausência de alerta não comprova cobertura completa.
- Profundidade e drenagem são amostras; fila vazia não comprova decisão correta de todas as propostas.
- Entrega na fila e registro JSONL não equivalem a reconhecimento humano. Responsável e escalonamento operacional continuam sem validação.
- A stack não detecta sozinha sua perda total; monitor externo permanece fora da entrega atual.

## Investigação e validação

1. Identificar alerta, ambiente, serviço, instância e início do episódio.
2. Conferir o frescor dos dados e abrir o runbook correspondente.
3. Correlacionar logs e traces; verificar decisões, pendências e DLQ da mesma coorte.
4. Restaurar o componente e comprovar recuperação do negócio e resolução do alerta separadamente.
5. Registrar impacto, ações e limites no [modelo de postmortem](../postmortem-template.md).

Silêncios e inibições afetam notificações, não os resultados medidos. Preservar falhas e resultados incompletos.

Validar os arquivos com promtool/amtool conforme o [manual técnico](../../../infrastructure/Configuration/observability/ALERTAS.md). Teste offline de regra não comprova entrega de notificação. Para testes de canal, usar um episódio sintético com expiração e distinguir entrada no Alertmanager, publicação, recepção e atendimento.

## Evidências

- [Validação S3](../../../aidlc-docs/construction/sre-s3/validation.md): regras de burn rate e testes offline.
- [Validação S4](../../../aidlc-docs/construction/sre-s4/validation.md) e [postmortem](../postmortem-gameday-s4-20261023.md): ciclo sintético local com deduplicação.
- [Falha real do worker, 17/10](../exercicio-falha-worker.md): pausa preservando o processo, recuperação de dez propostas e notificações até SNS.
- [Validação S0](../../../aidlc-docs/construction/sre-s0/validation.md): correção do detector OPEN/HALF_OPEN. Os relatos antigos preservam a lacuna observada antes da correção.
