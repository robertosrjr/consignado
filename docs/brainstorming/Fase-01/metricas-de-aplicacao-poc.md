# Especificação de Métricas de Aplicação e Engenharia — PoC Crédito Consignado

**Projeto:** PoC de Ingestão Massiva e Resiliência — Crédito Consignado  
**Status:** Definição Técnica de Telemetria  
**Padrão de Exposição:** Spring Boot Actuator / Micrometer (Prometheus Format)

---

## 1. Visão Geral
Esta especificação define o conjunto de **métricas de aplicação, concorrência, resiliência e infraestrutura** necessárias para garantir a observabilidade técnica da PoC. Enquanto as métricas de negócio monitoram o impacto financeiro (VGV, ticket médio, portabilidade), estas métricas asseguram que a solução suporte o estresse de **10.000 TPS na borda** mantendo o **Core Banking protegido em 180 TPS**.

---

## 2. Pilares de Métricas de Aplicação

### Pilar 1: Desempenho e SLA da Camada de Borda (Ingestion API)
Monitora a capacidade do ecossistema em absorver o pico inicial sem degradação.

| Métrica Micrometer / Prometheus | Tipo | Descrição / Objetivo | SLA / Target |
| :--- | :--- | :--- | :--- |
| `ingestion_requests_accepted_total` | Counter | Total de requisições de ingestão aceitas na borda. | Medir pico de **10.000 TPS** |
| `ingestion_request_latency_seconds` | Histogram | Latência p50, p95 e p99 da API `POST /v1/proposals`. | **p95 < 100 ms**, **p99 < 200 ms** |
| `ingestion_http_status_total` | Counter | Distribuição de respostas por código HTTP (202, 400, 429, 500). | **> 99.9% HTTP 202** |
| `ingestion_tenant_throttling_total` | Counter | Requisições bloqueadas por cota excedida por parceiro (`HTTP 429`). | Monitorar abusos de robôs B2B |

---

### Pilar 2: Proteção e Resiliência do Sistema Legado (Mock / Core Banking)
Garante que a carga repassada downstream respeite rigorosamente a capacidade física do legado.

| Métrica Micrometer / Prometheus | Tipo | Descrição / Objetivo | SLA / Target |
| :--- | :--- | :--- | :--- |
| `legacy_request_throughput_tps` | Gauge/Rate | Taxa de requisições por segundo enviadas ao legado. | **Estritamente <= 180 TPS** |
| `legacy_request_latency_seconds` | Histogram | Latência de resposta do legado (p50, p95, p99). | Identificar gargalos downstream |
| `resilience4j_circuitbreaker_state` | Gauge | Estado do Circuit Breaker (`0=CLOSED`, `1=OPEN`, `2=HALF_OPEN`). | Manter em `CLOSED` (0) |
| `resilience4j_ratelimiter_available_permissions` | Gauge | Permissões disponíveis no bucket do Rate Limiter. | Evitar exaustão de chamadas |
| `legacy_fallback_requests_total` | Counter | Total de chamadas que acionaram o mecanismo de fallback. | Alerta em caso de indisponibilidade |

---

### Pilar 3: Saúde de Mensageria e Filas (Amazon SQS & Dispatcher)
Acompanha o comportamento do buffer assíncrono e a governança de QoS.

| Métrica Micrometer / Prometheus | Tipo | Descrição / Objetivo | SLA / Target |
| :--- | :--- | :--- | :--- |
| `sqs_queue_depth_messages` | Gauge | Profundidade de mensagens acumuladas por fila (`fast-lane`, `standard-lane`). | Monitorar crescimento do backlog |
| `sqs_oldest_message_age_seconds` | Gauge | Idade da mensagem mais antiga na fila sem processamento. | Prever risco de estouro de SLA |
| `sqs_dlq_message_count` | Gauge | Total de mensagens falhas isoladas na *Dead Letter Queue*. | **Target: 0 mensagens na DLQ** |
| `dispatcher_routed_messages_total` | Counter | Volume de mensagens distribuídas por fila no SQS (Fast/Standard). | Manter proporção **70% / 30%** |

---

### Pilar 4: Recursos de Infraestrutura, Concorrência e Persistência
Instrumenta os componentes internos de runtime (Java 21, DynamoDB e Aurora PostgreSQL).

| Métrica Micrometer / Prometheus | Tipo | Descrição / Objetivo | SLA / Target |
| :--- | :--- | :--- | :--- |
| `jvm_threads_virtual_active` | Gauge | Número de Virtual Threads ativas em execução em Java 21. | Concorrência leve sem exaustão |
| `jvm_threads_virtual_pinned_total` | Counter | Incidentes de *Carrier Thread Pinning* (I/O bloqueante nativo). | **Target: 0 incidentes** |
| `dynamodb_conditional_check_failed_total` | Counter | Registros duplicados filtrados atômica e gratuitamente na borda. | Provar retenção de robôs/duplicatas |
| `dynamodb_write_latency_seconds` | Histogram | Latência de escrita da intenção na tabela `DurableInbox`. | **< 15 ms** |
| `hikaricp_connections_pending` | Gauge | Conexões JDBC aguardando no pool do Aurora PostgreSQL. | **Target: 0 pendências** |
| `outbox_events_lag_seconds` | Gauge | Atraso entre a criação e a gravação do evento na tabela `proposal_event`. | Rastreabilidade atômica |

---

## 3. Thresholds de Alertas (Alertmanager Rules)

```yaml
groups:
  - name: poc_consignado_alerts
    rules:
      - alert: CoreBankingOverloadRisk
        expr: rate(legacy_request_throughput_tps[5s]) > 180
        for: 2s
        labels:
          severity: critical
        annotations:
          summary: "Vazão ao legado ultrapassou o limite seguro de 180 TPS!"

      - alert: HighEdgeLatency
        expr: histogram_quantile(0.95, sum(rate(ingestion_request_latency_seconds_bucket[1m])) by (le)) > 0.100
        for: 10s
        labels:
          severity: warning
        annotations:
          summary: "Latência p95 da Ingestion API ultrapassou 100ms."

      - alert: CircuitBreakerOpened
        expr: resilience4j_circuitbreaker_state{name="legacyService"} == 1
        for: 0s
        labels:
          severity: critical
        annotations:
          summary: "Circuit Breaker do Legado abriu devido a falhas downstream."

      - alert: DLQMessagesDetected
        expr: sqs_dlq_message_count > 0
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "Mensagens não processadas acumulando na Dead Letter Queue."
```

---

## 4. Estrutura de Exibição do Painel no Grafana

* **Painel Superior (Borda & Ingestão):**
  * Gráfico de Linha: `ingestion_requests_accepted_total` (alvo 10.000 TPS).
  * Gauge de SLA: `ingestion_request_latency_seconds` (p95 / p99).
  * Stat Counter: `dynamodb_conditional_check_failed_total` (duplicatas filtradas).
* **Painel Intermediário (Roteamento & Filas SQS):**
  * Bar Chart: Proporção de mensagens `Fast Lane` (70%) vs `Standard Lane` (30%).
  * Time Series: `sqs_queue_depth_messages` (profundidade do backlog).
* **Painel Inferior (Proteção e Saúde do Legado):**
  * Single Stat com Linha Vermelha de Limite: `legacy_request_throughput_tps` (cravado em 180 TPS).
  * Status Indicator: Estado do Circuit Breaker (`CLOSED` / `OPEN`).
  * Gauge de Runtime: Concorrência de Virtual Threads (`jvm_threads_virtual_active`).
