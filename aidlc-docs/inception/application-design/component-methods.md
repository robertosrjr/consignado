# Component Methods

Assinaturas de alto nivel (Java 25). Regras de negocio detalhadas ficam no Functional Design por unidade. Tipos `Cpf`, `IdempotencyKey`, `TenantId`, `Money`, `ConvenioType`, `Lane` vem de `shared`.

## shared

| Tipo | Assinatura | Proposito |
|---|---|---|
| `Cpf` | `record Cpf(String digits)`; `static Cpf of(String raw)` | Valida digitos e verificadores; `toString()` mascarado |
| `IdempotencyKey` | `record IdempotencyKey(UUID value)`; `static IdempotencyKey parse(String raw)` | Valida UUID |
| `TenantId` | `record TenantId(String value)` | Valida formato e tamanho |
| `Money` | `record Money(BigDecimal amount)`; `Money add(Money)` | Valor nao negativo, 2 casas |
| `ProposalMessage` | `record ProposalMessage(int schemaVersion, IdempotencyKey key, TenantId tenant, Lane lane, int priorityScore)` | Contrato Dispatcher -> Worker (sem PII; o Worker le o item completo do inbox) |

Observacao: a mensagem SQS leva apenas referencia (`key`) e metadados de roteamento. O Worker busca o item completo no `DurableInbox` pela chave, o que mantem CPF fora da fila.

## ingestion-api

### AcceptProposalUseCase (port in)
| Metodo | Entrada | Saida | Proposito |
|---|---|---|---|
| `AcceptProposalResult accept(AcceptProposalCommand cmd)` | `AcceptProposalCommand(IdempotencyKey, TenantId, Cpf, ConvenioType, boolean portability, Money, int installments, TraceContext)` | `AcceptProposalResult(IngestionOutcome, IdempotencyKey, String tracingId, Instant receivedAt)` | Aplica cota, protege payload e grava de forma idempotente |

### Ports out
| Porta | Metodo | Proposito |
|---|---|---|
| `DurableInboxPort` | `InboxWriteResult putIfAbsent(InboxItem item)` | Escrita condicional; `InboxWriteResult` = `CREATED` ou `ALREADY_EXISTS` |
| `TenantQuotaPort` | `boolean tryAcquire(TenantId tenant)` | Consome 1 permissao da cota do tenant |
| `PayloadProtectionPort` | `ProtectedPayload protect(AcceptProposalCommand cmd)` | Serializa, comprime e cifra o payload |
| `TraceContextPort` | `String currentTracingId()` | Obtem o `tracingId` corrente |
| `ClockPort` | `Instant now()` | Tempo testavel |

### Adapters (in)
| Classe | Metodo | Proposito |
|---|---|---|
| `ProposalController` | `ResponseEntity<ProposalAcceptedResponse> create(@RequestHeader ..., @Valid @RequestBody ProposalRequest)` | Traduz HTTP para o caso de uso |
| `ProblemDetailExceptionHandler` | `ProblemDetail handle(Exception)` | Mapeia 400/401/429/500 sem vazar detalhes |

## dispatcher-lambda

| Classe | Metodo | Entrada | Saida |
|---|---|---|---|
| `InboxStreamHandler` | `void handleRequest(DynamodbEvent event, Context ctx)` | Registros de stream | - (falha parcial reportada por item) |
| `PriorityScorer` | `int score(RoutingAttributes attrs)` | `RoutingAttributes(TenantId, ConvenioType, boolean portability, Money amount)` | `priorityScore` |
| `LaneRouter` | `Lane route(int priorityScore)` | score | `Lane` |
| `QueuePublisher` | `void publish(Lane lane, ProposalMessage msg)` | lane, mensagem | - |
| `InboxStatusUpdater` | `void markDispatched(IdempotencyKey key, int priorityScore)` | chave, score | - |

## worker

### ProcessProposalUseCase (port in)
| Metodo | Entrada | Saida | Proposito |
|---|---|---|---|
| `ProcessingOutcome process(ProposalMessage message)` | mensagem SQS | `ProcessingOutcome` = `COMPLETED`, `RETRY`, `DEAD_LETTER` | Executa o fluxo PENDING -> legado -> estado final |

### Ports out
| Porta | Metodo | Proposito |
|---|---|---|
| `ProposalRepositoryPort` | `Proposal createPendingIfAbsent(ProposalSeed seed)` | Cria `PENDING` idempotente (`idempotency_key UNIQUE`) |
| | `void save(Proposal proposal, List<ProposalEvent> events)` | Persiste estado e eventos na mesma transacao |
| | `Optional<Proposal> findByKey(IdempotencyKey key)` | Retomada apos falha |
| `InboxReadPort` | `Optional<InboxRecord> load(IdempotencyKey key)` | Le o item completo (decifra o CPF) |
| `LegacyCorePort` | `LegacyResult submit(LegacySubmission submission)` | Chama o legado com `Idempotency-Key`; circuit breaker interno |
| `ThroughputBudgetPort` | `boolean tryAcquire(Lane lane)` | Consome 1 permissao do orcamento da lane |
| | `BudgetSnapshot snapshot()` | Uso corrente por lane (para o alocador) |
| `ClockPort` | `Instant now()` | Tempo testavel |

### Domain
| Tipo | Metodo | Proposito |
|---|---|---|
| `Proposal` | `Proposal markSentToLegacy()`, `confirm()`, `fail(String reason)` | Transicoes validas da maquina de estados; lanca `IllegalStateTransitionException` |
| `LaneBudgetAllocator` | `Lane nextLane(BudgetSnapshot snapshot, QueueDepths depths)` | Piso 126/54, emprestimo de ociosa, teto 180 (funcao pura) |

### Application
| Classe | Metodo | Proposito |
|---|---|---|
| `LaneScheduler` | `Optional<Lane> pickLane(QueueDepths depths)` | Usa `LaneBudgetAllocator` e `ThroughputBudgetPort` |
| `ProcessProposalService` | `ProcessingOutcome process(ProposalMessage m)` | Orquestra o caso de uso |

### Adapters
| Classe | Metodo | Proposito |
|---|---|---|
| `SqsLaneConsumer` | `void poll(Lane lane)` | Loop com Virtual Threads; ack somente apos `COMPLETED` ou `DEAD_LETTER` |

## mock-legacy

| Classe | Metodo | Proposito |
|---|---|---|
| `AverbacaoController` | `ResponseEntity<AverbacaoResponse> averbar(@RequestHeader("Idempotency-Key") String key, @RequestBody AverbacaoRequest req)` | `200` (novo ou repetido), `503` (acima de 180 TPS ou falha simulada) |
| `CapacityGate` | `boolean tryEnter()` | Janela deslizante de 1 s |
| `IdempotencyStore` | `Optional<AverbacaoResponse> find(String key)`; `void put(String key, AverbacaoResponse r)` | Resultado repetido; mapa limitado |
