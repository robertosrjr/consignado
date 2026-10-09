# Invariantes: código, testes e glossário

## 1. Mapa invariante → código → teste

Caminhos relativos a `applications/`. A coluna "Teste exigido" vale para quem alterar o código da linha.

| ID | Onde vive | Teste existente | Teste exigido ao alterar |
|---|---|---|---|
| I1 | `ingestion-api/.../usecase/AcceptProposalService.java` | `AcceptProposalServiceTest`, `IngestionArchTest` | ArchUnit: `ingestion` não depende de SQS, de `LegacyCorePort` nem de cliente HTTP de saída |
| I2 | `ingestion-api/.../persistence/DynamoDbInboxAdapter.java`, `shared/.../vo/IdempotencyKey.java` | `DynamoDbInboxAdapterIT`, `AcceptProposalServiceProperties` | propriedade: dois tenants distintos com o mesmo UUID → os dois gravados, e nenhuma resposta com dados do outro |
| I3 | idem I2 + `IngestionExceptionHandler` | — | mesma chave e payload diferente → `422` Problem Details e métrica `ingestion.idempotency.payload_mismatch` |
| I4 | idem I2 (`TransactWriteItems`) | — | 500 UUIDs distintos com a mesma proposta → 1 item técnico, 1 fingerprint e 499 `DUPLICATE_ACCEPTED`; o fingerprint nunca aparece em log (`PiiNeverReachesTheConsoleTest`) |
| I5 | `dispatcher-lambda/.../dispatch/StreamRecordView.java` (`isInsert`), `DispatchService` | `DispatchServiceTest`, `BatchDispatcherTest` | um item de fingerprint em `INSERT` → `SKIPPED`; `MODIFY` de status → `SKIPPED` |
| I6 | `worker/.../budget/FixedPerTaskBudgetAdapter.java`, `domain/TokenBucket.java`, `usecase/ProposalDispatchLoop.java` | `TokenBucketProperties`, `BudgetSimulationTest`, `AverbacaoCapacityTest` | propriedade: em qualquer janela de 1s, permissões ≤ `capacidade + taxa`; o mock com `legacy.peak.window ≤ 180` |
| I7 | `worker/.../domain/LaneBudgetAllocator.java` | `LaneBudgetAllocatorProperties` | propriedade: com as duas lanes com mensagens, cada uma recebe pelo menos o seu piso em qualquer janela |
| I8 | `worker/.../domain/Proposal.java`, `ProposalStatus.java` | `ProposalProperties`, `ProposalTest` | propriedade: nenhuma sequência de transições sai de um estado terminal |
| I9 | `worker/.../usecase/ProposalDispatchLoop.java` (`queue.release` + `RetryBackoff`), `legacy/LegacyHttpAdapter.java` | `ProposalDispatchLoopTest`, `RetryBackoffTest`, `LegacyHttpAdapterTest` | um `TransientFailure` gera `RETRY` e uma única chamada HTTP por entrega de mensagem |
| I10 | `LegacyHttpAdapter` (distinguir antes e depois do envio), `ProcessProposalService`, `mock-legacy` | — | `mock-legacy` com `idempotent=false` e `commit-then-timeout` → zero duplicatas por chave de negócio; com a reconciliação desligada, o teste **falha** |
| I11 | `ProposalRepositoryPort` + novo adaptador DynamoDB | — | duas instâncias, visibility timeout de 2s e legado com 5s de latência → zero chamadas concorrentes por chave; escrita com versão antiga é rejeitada |
| I12 | nova `EligibilityPort` no worker | — | restrição injetada depois do `202` → `REJECTED_PRE_LEGACY` e zero chamadas ao legado |
| I13 | consumidor do Stream para notificação (novo) | — | queda do webhook do parceiro não altera a vazão do worker |
| I14 | `shared/.../contract/ProposalMessage.java`, `platform-starter/LogSanitizer`, `PiiRedactingJsonMembersCustomizer` | `PiiNeverReachesTheConsoleTest`, `ProposalMessageCodecTest` | o codec da mensagem de fila não tem campo de CPF; o teste de console continua verde |
| I15 | `worker/.../domain/ProposalEvent.java` | `ProcessProposalServiceTest` | toda transição nova emite um evento |

## 2. Glossário de estados

Os estados existentes **não** são renomeados. Os estados novos usam inglês em maiúsculas, como o código.

### DurableInbox (`InboxAttributes.STATUS_*`)

| Estado | Significado |
|---|---|
| `RECEIVED` | aceito pela borda, aguardando roteamento |
| `DISPATCHED` | enviado a uma lane pelo dispatcher |
| `FAILED` | não roteável |

### Proposta no worker (`ProposalStatus`)

| Estado | Situação | Significado |
|---|---|---|
| `PENDING` | existe | criada, sem chamada ao legado |
| `SENT_TO_LEGACY` | existe | chamada ao legado iniciada. Hoje pode ser repetida com a mesma chave; a M2 a substitui na prática pelo par `SENT_TO_LEGACY` + `UNKNOWN` |
| `CONFIRMED` | existe, terminal | averbação confirmada |
| `FAILED` | existe, terminal | rejeição de negócio do legado |
| `UNKNOWN` | M2 | timeout ou erro depois do envio; exige consulta antes de qualquer reenvio |
| `MANUAL_REVIEW` | M2, terminal | consulta indisponível após N tentativas |
| `EXPIRED` | M5, terminal | passou da validade de negócio antes do efeito |
| `REJECTED_PRE_LEGACY` | M5, terminal | negada pela guarda de efetivação |

No pattern, os mesmos conceitos aparecem em português ("rejeitada", "expirada"). São nomes conceituais; no código vale esta tabela.

## 3. Notas de AWS que afetam as invariantes

- **Escrita condicional no DynamoDB:** consome capacidade de escrita mesmo quando a condição falha. Duplicatas são baratas, mas não são de graça.
- **`TransactWriteItems`** (M1) custa o dobro de capacidade e falha inteira se qualquer condição falhar. Trate `TransactionCanceledException` lendo os `CancellationReasons` para distinguir duplicata técnica de duplicata de negócio.
- **DynamoDB Streams:**
  - cada item da transação gera um registro próprio;
  - itens de fingerprint chegam como `INSERT` e precisam ser filtrados (I5); de preferência com *filter criteria* no event source mapping, e também no código, como defesa;
  - retenção de 24h, com alarme de *iterator age*.
- **Falha parcial de lote na Lambda** (`batchItemFailures`): o checkpoint volta ao **primeiro** registro com falha, e tudo depois dele é reprocessado. Pare no primeiro erro (M8).
- **SQS standard:**
  - entrega pelo menos uma vez e sem ordem;
  - se o processamento passar do visibility timeout (padrão 30s), outra instância recebe a mesma mensagem. Por isso existem I8, I10 e I11;
  - renove a visibilidade enquanto a chamada ao legado estiver em curso;
  - o redrive para a DLQ é o limite de tentativas.
- **Tabela nova em on-demand:** não nasce aceitando 10k escritas/s. Avalie *warm throughput* antes do teste de carga (M6).

## 4. Configuração que faz parte das invariantes

| Propriedade | Padrão | Invariante | Regra |
|---|---|---|---|
| `worker.budget.total-tps` | 180 | I6 | só muda com novo número de capacidade do legado |
| `worker.budget.instances` | 1 | I6, I11 | precisa ser igual ao número real de instâncias; com repositório em memória, **sempre 1** |
| `worker.budget.fast-share` | 0.70 | I7 | só muda por decisão de negócio |
| `worker.budget.safety-margin` | 0.98 | I6 | nunca acima de 1 |
| `worker.max-in-flight` | 360 | I6 | ≥ 180 × latência p99 do legado em segundos (Lei de Little) |
| `worker.queue.visibility-timeout-seconds` | 30 | I10, I11 | > `worker.legacy.request-timeout` + margem |
| `worker.legacy.request-timeout` | 3s | I10 | timeout de leitura depois do envio = resultado desconhecido |
