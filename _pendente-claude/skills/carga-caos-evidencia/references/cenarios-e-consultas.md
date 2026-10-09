# Cenários, consultas e contas

## 1. Cenários

Local sugerido para os scripts: `load-tests/k6/<cenario>.js`, a criar na U7. Cada script lê a URL da borda e o emissor de token por variável de ambiente.

| Cenário | Carga | Prova | Invariante | Critério de aprovação |
|---|---|---|---|---|
| `spike` | 50 → 10.000 req/s em 1s, sustentado por 5 min | absorção e latência do `202` | I1, I6 | > 99,9% de `202`; p95 < 100 ms; `legacy_peak_window` ≤ 180 durante todo o teste |
| `duplicate-storm` | 500× a mesma chave no mesmo segundo | idempotência técnica | I2 | 1 `RECEIVED` e 499 `DUPLICATE_ACCEPTED`; `legacy_averbacoes_total{kind="duplicate"}` = 0 |
| `robot-new-uuid` | a mesma proposta com UUID novo a cada disparo | identidade de negócio | I4 | uma averbação por proposta de negócio (M1) |
| `cross-tenant` | dois tenants com o mesmo UUID | isolamento | I2, I14 | as duas propostas gravadas; nenhuma resposta com dado do outro tenant |
| `payload-mismatch` | a mesma chave com payload diferente | integridade | I3 | `422` e nenhum item novo (M1) |
| `legacy-commit-then-timeout` | carga constante a 180 TPS com caos de 5% | resultado desconhecido | I10 | zero duplicatas por chave de negócio; com a reconciliação desligada, deve haver duplicatas (teste negativo) |
| `worker-kill` | matar o worker no meio da carga e reiniciar | durabilidade e reprocessamento | I8, I9 | zero perda (aceitas = concluídas + na fila + DLQ); zero duplicatas |
| `three-replicas` | três instâncias do worker | teto e exclusão | I6, I11 | `legacy_peak_window` ≤ 180; zero chamadas concorrentes por chave (depende da M3) |
| `legacy-degraded` | capacidade do mock reduzida para 120 TPS no meio do teste | limite adaptativo | I6 | sem cascata de `503` (depende da M4) |
| `late-restriction` | restrição injetada depois do `202` | guarda de efetivação | I12 | `REJECTED_PRE_LEGACY` e zero chamadas ao legado para aquela proposta (M5) |
| `status-storm` | 300 mil pendentes consultando a cada 2s | proteção da leitura | I13 | escrita mantém o p95; excesso de consulta recebe `429`, não `5xx` (M9) |
| `drain` | depois do `spike`, sem carga nova | tempo de drenagem | — | `effective_drain_time` converge e bate com a conta da seção 3 com erro ≤ 20% |

## 2. Consultas no servidor

Nomes no formato do Prometheus, derivados dos medidores Micrometer existentes. Confirme os nomes reais em `/actuator/prometheus` antes de publicar.

| O que medir | Consulta |
|---|---|
| Pico por janela no legado | `max_over_time(legacy_peak_window[5m])` |
| Averbações únicas e duplicadas | `increase(legacy_averbacoes_total[5m])` por `kind` |
| Recusas por capacidade no legado | `increase(legacy_requests_total{outcome="rejected_capacity"}[5m])` |
| Resultado na borda | `sum by (outcome) (rate(ingestion_accepted_total[1m]))` |
| Throttling por tenant | `rate(ingestion_throttled_tenant_total[1m])` |
| Saturação do worker | `worker_in_flight / worker_in_flight_max` |
| Profundidade das filas | `worker_queue_depth` |
| Taxas de chegada e de processamento | `worker_arrival_rate`, `worker_processing_rate` |
| Tempo estimado de drenagem | `effective_drain_time` |
| Crescimento do backlog | `net_backlog_growth` |
| Estado do circuit breaker | `worker_legacy_circuit_state` |
| Latência da chamada ao legado | `histogram_quantile(0.99, rate(worker_legacy_call_seconds_bucket[1m]))` |
| Tempo até o efeito | `histogram_quantile(0.95, rate(business_time_to_settle_seconds_bucket[5m]))` |

SQS, DLQ, Lambda e DynamoDB vêm do CloudWatch: `ApproximateNumberOfMessagesVisible`, `ApproximateAgeOfOldestMessage`, `IteratorAge`, `ConsumedWriteCapacityUnits` e `ThrottledRequests`.

## 3. Contas

**Tempo de drenagem**, para 3 milhões de requisições em 5 minutos:

| Propostas únicas | Volume | A 180 TPS |
|---|---|---|
| 10% | 300 mil | ~28 min |
| 30% | 900 mil | ~83 min |
| 100% | 3 milhões | ~4h38 |

Na lane standard, com o piso de 54 TPS e a fast saturada, multiplique por 180 ÷ 54 para a parcela standard.

**Lei de Little:** concorrência necessária = vazão × latência. A 180 TPS e 1s de latência p99, são necessárias 180 chamadas em voo. O padrão `max-in-flight = 360` cobre até 2s de latência.

**Escrita no DynamoDB, no pico:**
`req/s × itens por requisição × (2 se transação) × ceil(tamanho do item em KB)`.

| Cenário | Conta | Resultado |
|---|---|---|
| Hoje (1 item, sem transação, ~1 a 2 KB) | 10.000 × 1 × 1 × 1–2 | 10 mil a 20 mil WCU/s |
| Depois da M1 (2 itens, com transação) | 10.000 × 2 × 2 × 1–2 | 40 mil a 80 mil WCU/s |

Confirme o warm throughput da tabela antes de rodar o `spike`.
