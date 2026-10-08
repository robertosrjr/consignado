# Services

Servicos = casos de uso de aplicacao e funcoes de orquestracao. Implementam as portas `in` e coordenam portas `out`; nao contem detalhe de infraestrutura.

## S1. AcceptProposalService (ingestion-api)

**Responsabilidade**: aceitar a proposta com durabilidade e idempotencia, respondendo dentro do SLA.

**Orquestracao**
1. `TenantQuotaPort.tryAcquire(tenant)`; negado -> `TenantQuotaExceededException` (HTTP 429).
2. Montar `IngestedProposal` (validacao pelos value objects; invalido -> `InvalidProposalException`, HTTP 400).
3. `PayloadProtectionPort.protect(...)` (comprime e cifra o payload com CPF).
4. `DurableInboxPort.putIfAbsent(item)`:
   - `CREATED` -> `IngestionOutcome.RECEIVED`.
   - `ALREADY_EXISTS` -> `IngestionOutcome.DUPLICATE_ACCEPTED` (sem reenfileirar).
5. Retornar `AcceptProposalResult` com `tracingId` e `receivedAt`.

**Regras de resiliencia**: nenhuma chamada a legado ou Aurora na borda; falha do DynamoDB -> `503` com `Retry-After` (a proposta nao foi aceita, nada a compensar).

## S2. Dispatch flow (dispatcher-lambda)

**Responsabilidade**: transformar cada novo item do inbox em uma mensagem na lane correta.

**Orquestracao** (por registro INSERT)
1. Extrair `RoutingAttributes` do item (sem decifrar o payload).
2. `PriorityScorer.score(attrs)` -> `priorityScore`.
3. `LaneRouter.route(score)` -> `Lane`.
4. `QueuePublisher.publish(lane, ProposalMessage)` (com `traceparent`).
5. `InboxStatusUpdater.markDispatched(key, score)` (condicional: so de `RECEIVED` para `DISPATCHED`, torna reprocessamento do stream seguro).

**Falhas**: erro em um registro -> retorno de falha parcial (`batchItemFailures`); apos tentativas, evento vai para DLQ do stream/`sqs-dlq`. Publicar duas vezes a mesma chave e tolerado, pois o Worker e idempotente.

## S3. ProcessProposalService (worker)

**Responsabilidade**: levar a proposta ate o estado final sem averbacao duplicada.

**Orquestracao**
1. `InboxReadPort.load(key)`; ausente -> `DEAD_LETTER`.
2. `ProposalRepositoryPort.createPendingIfAbsent(seed)`:
   - se o estado ja e final (`CONFIRMED`/`FAILED`) -> `COMPLETED` (mensagem duplicada, nada a fazer).
3. `Proposal.markSentToLegacy()`; `save(proposal, [ProposalEvent])`.
4. `LegacyCorePort.submit(...)` com `Idempotency-Key`:
   - `CONFIRMED` -> `proposal.confirm()` -> `save` -> `COMPLETED`.
   - `REJECTED` -> `proposal.fail(reason)` -> `save` -> `COMPLETED`.
   - `TRANSIENT_FAILURE` ou circuito aberto -> `RETRY` (mensagem volta a fila; recebimento > 3 -> DLQ).
5. Cada transicao grava `ProposalEvent` na mesma transacao do estado (outbox como historico auditavel, D12).

**Garantia**: reenvio ao legado so ocorre com a mesma `Idempotency-Key`, entao o legado devolve o mesmo resultado; nao ha retry cego de escrita financeira.

## S4. LaneScheduler (worker)

**Responsabilidade**: dividir o orcamento global de 180 TPS entre as lanes.

**Orquestracao**
1. Obter `QueueDepths` (aproximado) e `BudgetSnapshot` (`ThroughputBudgetPort`).
2. `LaneBudgetAllocator.nextLane(...)` aplica: piso Fast 126 / Standard 54; lane vazia empresta capacidade; teto total 180.
3. `ThroughputBudgetPort.tryAcquire(lane)`; sem permissao -> aguardar proximo intervalo.
4. Entregar a mensagem da lane escolhida ao `ProcessProposalService`.

**Limitacao documentada**: `FixedPerTaskBudgetAdapter` divide 180 por N tarefas (N configuravel); a protecao e local por instancia, com o Mock Legacy medindo o pico real.

## S5. Legado simulado (mock-legacy)

1. `CapacityGate.tryEnter()`; falso -> `503`.
2. `IdempotencyStore.find(key)`; presente -> `200` com resultado anterior (sem nova averbacao).
3. Aplicar `ChaosProperties` (latencia/erro), registrar nova averbacao, guardar resultado.

## Padroes transversais

| Preocupacao | Padrao | Onde |
|---|---|---|
| Idempotencia | Escrita condicional (DynamoDB), `UNIQUE` (Aurora), `Idempotency-Key` (legado) | S1, S3, S5 |
| Protecao do legado | Rate limit por orcamento + circuit breaker | S3, S4 |
| Rastreabilidade | `traceparent` em HTTP e em atributos de mensagem SQS/Stream; `tracingId` na resposta | Todos |
| Privacidade | CPF cifrado no inbox, ausente de mensagens, logs e metricas | S1, S2, S3 |
| Erros | `ProblemDetail` sem vazamento | S1, S5 |
