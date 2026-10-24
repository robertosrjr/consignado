# S2 — Functional Design: medição contínua

Data: 23/10/2026. Base: `docs/sre/slo.md`. Execução local.

## Problema central

O SLO de J2 exige saber, para **cada proposta aceita**, se houve decisão correta até `aceite + prazo da faixa`, inclusive as que ainda estão na fila e as que nunca foram decididas. Hoje:

- a Inbox (DynamoDB, chave `idempotencyKey`) guarda só o despacho: `RECEIVED`, `DISPATCHED`, `FAILED`, mais `createdAt`, `lane` e `routedAt`;
- a decisão (`CONFIRMED`/`FAILED`) vive em `InMemoryProposalRepository` do worker e se perde no reinício;
- `business.time.to.settle` só registra as propostas decididas.

## Opções para o avaliador de prazos

| | A — decisão gravada na Inbox + índice de prazo (recomendada) | B — avaliador em memória no worker |
|---|---|---|
| Como | O dispatcher grava `dueAt = createdAt + prazo(lane)` ao rotear. O worker grava `decision` e `decidedAt` no mesmo item. GSI esparso `openDue` (`dueBucket`, `dueAt`) só para itens sem decisão. Um avaliador consulta os itens vencidos e emite contadores bons/ruins por faixa. | Cada proposta consumida entra num mapa com prazo; o avaliador varre o mapa. |
| Cobertura | Todas as aceitas, inclusive na fila, na DLQ ou com worker parado | Só as que o worker já recebeu; fila e DLQ ficam invisíveis |
| Durabilidade | Sobrevive a reinício (DynamoDB Local) | Perde tudo no reinício |
| Custo | Toca dispatcher, worker e a criação da tabela; adianta parte da S5 | Só worker |

## Evento e métrica (opção A)

- Bom: `decidedAt <= dueAt` e `decision` em {confirmed, business_rejected}.
- Ruim: `decidedAt > dueAt`, `technical_failure`, ou vencido sem decisão no corte do avaliador.
- Contador `slo.j2.events{lane, outcome=good|bad}`, incrementado uma vez por proposta, quando ela é classificada. O avaliador marca o item como `sloEvaluated` (escrita condicional) para não contar duas vezes, inclusive com várias réplicas.
- Gauge `slo.j2.open.overdue{lane}`: itens vencidos ainda sem decisão.

## Demais entregas da S2 (independentes da escolha)

1. `business.time.to.settle`: buckets de 120 s e 2700 s.
2. `http.server.requests` da ingestion-api: SLO buckets de 100 ms e 200 ms.
3. `worker.queue.oldest.age.seconds{lane}` e `worker.dlq.depth`, via atributos SQS (ElasticMQ local).
4. Recording rules `slo:j1_availability:ratio_rate{5m,30m,1h,6h,30d}`, `slo:j1_latency_100ms:ratio_rate*` e `slo:j2_deadline:ratio_rate*{lane}`, mais o budget restante em 30 dias, com testes promtool.
5. Dashboard "SLO e error budget" versionado (JSON), sem publicação.

Fora da S2: scrape direto no lugar do Pushgateway (não há Prometheus local rodando; fica para quando a pilha local subir) e o contador de conciliação 202 ↔ Inbox (vai para a S5, junto com o Sweeper).

Labels: apenas `lane` e `outcome`; nenhum ID de proposta.
