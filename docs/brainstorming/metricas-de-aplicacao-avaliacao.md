# Avaliação das métricas de aplicação da PoC

Base: `metricas-de-aplicacao-poc.md`, confrontada com o que o código emite hoje (Micrometer, Prometheus). Nomes no formato Prometheus.

## Resumo

- **Cobertas** (com outro nome ou por derivação): 10 de 21.
- **Parciais ou com ajuste de nome**: 4.
- **Não existem e valem a pena**: 4 (DLQ e idade da fila, latência do DynamoDB, fallback, permissões do rate limiter do worker).
- **Não se aplicam à fase atual**: 3 (Hikari, outbox em banco, pinning de virtual threads como alerta).
- **Há erros nos alertas** que fariam o painel mentir. Ver seção 3.

## 1. Métrica a métrica

### Pilar 1: borda (Ingestion API)

| Proposta | Situação | O que usar |
|---|---|---|
| `ingestion_requests_accepted_total` | Coberta, outro nome | `ingestion_accepted_total{outcome}` (soma de `received` + `duplicate_accepted`). TPS = `rate(...)` |
| `ingestion_request_latency_seconds` | Coberta, outro nome | `http_server_requests_seconds_bucket{uri="/v1/proposals"}` (histograma já ligado). p95 e p99 por `histogram_quantile` |
| `ingestion_http_status_total` | Coberta, outro nome | `http_server_requests_seconds_count` por `status` |
| `ingestion_tenant_throttling_total` | Coberta, outro nome | `ingestion_throttled_tenant_total{tenant}` (tenant sob allowlist) |

Observação: a latência de borda medida no servidor não inclui fila de aceitação do Tomcat nem rede. A latência que o cliente vê só sai do k6. Vale manter as duas.

### Pilar 2: legado

| Proposta | Situação | O que usar |
|---|---|---|
| `legacy_request_throughput_tps` | Parcial | `legacy_request_throughput` (gauge do mock, janela de 1 s) e **`legacy_peak_window`** (maior janela já vista). Existem só no mock |
| `legacy_request_latency_seconds` | Coberta, outro nome | `legacy_request_latency_seconds_bucket` (mock) e `worker_legacy_call_seconds_bucket{outcome}` (visão do worker, a que importa em produção) |
| `resilience4j_circuitbreaker_state` | **Não existe** | Não há `resilience4j-micrometer` no projeto. Existe `worker_legacy_circuit_state`, com **codificação diferente**: 0 fechado, 1 semi-aberto, **2 aberto** |
| `resilience4j_ratelimiter_available_permissions` | **Não existe** | O worker usa token bucket próprio, não o rate limiter do Resilience4j. A ingestão usa Resilience4j para cota por tenant, sem métrica exposta |
| `legacy_fallback_requests_total` | **Não existe** | Não há fallback: falha transitória vira retry na fila. O equivalente é `worker_processed_total{outcome="retry"}` e `worker_processing_failed_total` |

### Pilar 3: filas

| Proposta | Situação | O que usar |
|---|---|---|
| `sqs_queue_depth_messages` | Coberta, outro nome | `worker_queue_depth{lane}` (amostrado a cada N s) |
| `sqs_oldest_message_age_seconds` | **Não existe** | Na AWS vem do CloudWatch (`ApproximateAgeOfOldestMessage`). Local, não há |
| `sqs_dlq_message_count` | **Não existe** | O worker não lê a DLQ. Na AWS: CloudWatch `ApproximateNumberOfMessagesVisible` da DLQ |
| `dispatcher_routed_messages_total` | Parcial | O Dispatcher roda em Lambda e emite EMF no CloudWatch (`RecordsDispatched` por `Lane`), não em Prometheus. No Prometheus, a proporção efetiva é `worker_dispatched_total{lane}` |

Atenção: 70/30 é piso garantido **sob contenção**, com empréstimo. Se a Standard está vazia, a Fast usa tudo. O painel deve mostrar a proporção só quando as duas filas têm backlog; senão a "violação" é comportamento correto.

### Pilar 4: runtime e persistência

| Proposta | Situação | O que usar |
|---|---|---|
| `jvm_threads_virtual_active` | **Disponível, falta a dependência** (correção) | O Micrometer **expõe** threads virtuais com `io.micrometer:micrometer-java21` (métricas `jvm.threads.virtual.*`). Hoje a dependência não está no classpath (só `micrometer-core`, `-observation`, `-jakarta9`, `-registry-prometheus`). O BOM do Spring Boot 4.1.1 não gerencia a versão, então entra fixada (1.17.1, igual ao `micrometer-core`). Alto valor aqui: a plataforma liga virtual threads em todos os serviços |
| `jvm_threads_virtual_pinned_total` | Não se aplica como alerta | No Java 25 (JEP 491) `synchronized` não prende mais a carrier thread. Sobra I/O nativo, raro aqui. Basta o evento JFR `jdk.VirtualThreadPinned` em teste de carga |
| `dynamodb_conditional_check_failed_total` | Coberta, outro nome | `ingestion_accepted_total{outcome="duplicate_accepted"}` |
| `dynamodb_write_latency_seconds` | **Não existe** | Falta um timer em volta de `putIfAbsent`. É o número que sustenta o p95 < 100 ms. **Recomendo criar** |
| `hikaricp_connections_pending` | Não se aplica | Fase 1 usa persistência em memória, sem JDBC |
| `outbox_events_lag_seconds` | Não se aplica | O outbox é em memória e atômico com o estado. Sem atraso a medir. Volta com o banco real |

Texto do documento a corrigir: cita **Java 21** e **Aurora PostgreSQL**. A PoC usa Java 25 e persistência em memória.

## 2. O que o documento não cobre e deveria

| Métrica | Situação | Por quê |
|---|---|---|
| `worker_processed_total{lane,outcome}` | Existe | Taxa de retry, não processável e conclusão por lane |
| `worker_proposals{status}` | Existe | Evidência de idempotência: nenhuma proposta com duas averbações |
| `legacy_averbacoes_total{kind}` | Existe (mock) | `duplicate` deve ser zero. É a prova de "sem dupla averbação" |
| `net_backlog_growth`, `effective_drain_time` | Existem | Já no painel de negócio; entram aqui também |
| Utilização do orçamento por lane e quantidade emprestada | **Não existe** | Mostra o empréstimo 70/30 e o teto de 180 funcionando. Alto valor para a demonstração |
| Requisições em voo no worker | **Não existe** | Detecta saturação do semáforo de concorrência |
| Threads do Tomcat | **Não existe** | Exige `server.tomcat.mbeanregistry.enabled=true` |
| JVM padrão (heap, GC, CPU) | Existe | Já vem do Micrometer; falta só o painel |

## 3. Erros nos alertas propostos

| Alerta | Problema | Correção |
|---|---|---|
| `CoreBankingOverloadRisk`: `rate(legacy_request_throughput_tps[5s]) > 180` | `rate()` é só para counters; sobre gauge dá resultado sem sentido. Janela de 5 s com scrape de 15 s não tem amostra suficiente. `for: 2s` é menor que o scrape | `legacy_peak_window > 180` (pico em janela de 1 s, que o scrape de 15 s perderia) e, no lado do worker, `rate(worker_legacy_call_seconds_count[30s]) > 180` |
| `HighEdgeLatency` | Janela de 1 min e `for: 10s` ok, mas a métrica citada não existe | Usar `http_server_requests_seconds_bucket{uri="/v1/proposals"}` |
| `CircuitBreakerOpened`: `resilience4j_circuitbreaker_state == 1` | Métrica inexistente e `1` é semi-aberto aqui. Aberto é `2` | `worker_legacy_circuit_state == 2` |
| `DLQMessagesDetected` | Métrica inexistente | Alarme do CloudWatch na DLQ (ou exporter), `> 0` por 1 min |

Também faltam alertas de maior valor para o objetivo da PoC:

- `effective_drain_time > 1800` (backlog não esvazia em 30 min).
- `increase(legacy_averbacoes_total{kind="duplicate"}[5m]) > 0` (dupla averbação).
- `rate(ingestion_accepted_total[1m])` próximo da cota agregada (saturação da borda).

## 4. Recomendação (o que implementar agora)

Alto valor, baixo custo:

1. Timer `ingestion.inbox.write` em volta do `putIfAbsent` (latência do DynamoDB, com histograma).
2. Gauges de orçamento por lane: créditos usados, emprestados e utilização do teto global.
3. Gauge `worker.in.flight` (semáforo de concorrência).
4. `server.tomcat.mbeanregistry.enabled=true` na borda.
5. Corrigir os nomes e a codificação do circuit breaker na especificação, e reescrever os alertas.

6. Dependência `io.micrometer:micrometer-java21` (versão fixada em 1.17.1) para `jvm.threads.virtual.*`.

Adiar:

- DLQ e idade da fila em Prometheus: na AWS use CloudWatch (já há EMF no Dispatcher). Local, só se houver necessidade.
- Pinning de virtual threads: medir por JFR no teste de carga, sem métrica permanente.
- Hikari e outbox lag: voltam com o banco real.

## 5. Decisão: CloudWatch como fonte de dados (aprovada)

O Prometheus do EC2 de observabilidade raspa a aplicação; SQS (DLQ, idade) e Lambda vêm do CloudWatch na mesma instância do Grafana. Fonte `CloudWatch` provisionada em `applications/observability/grafana/provisioning/datasources/datasources.yml` (região `sa-east-1`, credenciais pela role do EC2). Política IAM somente leitura e mapa de métricas por painel em `applications/observability/grafana/CLOUDWATCH.md` e `cloudwatch-iam-policy.json`.

Com isso, DLQ e idade da fila saem do "adiar" da seção 4: passam a vir do CloudWatch, sem código novo na aplicação.
