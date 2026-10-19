# Software Design Description (SDD): Arquitetura SRE, Resiliência e Níveis de Serviço (SLI/SLO) — PoC Crédito Consignado

> **Padrão Documental:** IEEE 1016-2009 / ISO/IEC/IEEE 42010:2011 (Markdown SDD) [9, 14, 218]  
> **Target Agent / Team:** AI Developer (Cursor / Copilot / Claude Code), Staff Architects, SRE & Core Engineering Teams  
> **Tech Stack:** Java 21 (Virtual Threads), Spring Boot 3.3+, AWS (DynamoDB, SQS Multi-Lane, Lambda, ECS Fargate, KMS), Prometheus, Grafana, Alertmanager  
> **Status:** Approved for Engineering & AI Code Generation (Ready for Pipeline) [1]  
> **Versão:** 3.0.0-PROD-REALITY  

---

## 1. Contexto de Engenharia & Diagnóstico das Trincheiras

### 1.1 O Desafio Físico: O Paradoxo da Virada de Folha
No mercado brasileiro de crédito consignado (INSS, eSocial/CLT e Servidores Públicos), o momento de liberação de margem — a "virada de folha" — gera uma corrida de milissegundos entre instituições financeiras [5]. As 00:01 do dia da virada, o volume de tráfego salta de **50 TPS** para uma avalanche pontual de **10.000 TPS** sustentada por cerca de 5 minutos (totalizando ~3.000.000 de propostas) [206].

O *Core Banking* legado e os barramentos governamentais (Dataprev, eConsig, eSocial, Prodesp) possuem uma capacidade operacional física máxima e intransponível de **200 TPS** [50, 206]. Tentar forçar 10.000 TPS síncronos contra o legado resulta em queda catastrófica por exaustão de conexões, *cascading failures* e perda massiva de contratos para a concorrência [5, 6].

```
                     ┌──────────────────────────────────────────────────────────┐
                     │ Borda de Ingestão (10.000 TPS Peak / HTTP 202 Accepted)   │
                     └────────────────────────────┬─────────────────────────────┘
                                                  │
                                                  ▼
                     ┌──────────────────────────────────────────────────────────┐
                     │ DurableInbox (DynamoDB Sharded) + AWS SQS Multi-Lane     │
                     │ (Desacoplamento Temporal e Proteção Anti-Starvation)     │
                     └────────────────────────────┬─────────────────────────────┘
                                                  │
                                                  ▼ (Rate Limiter <= 180 TPS)
                     ┌──────────────────────────────────────────────────────────┐
                     │ Core Banking Legado / Dataprev (Capacidade Max: 200 TPS) │
                     └──────────────────────────────────────────────────────────┘
```

### 1.2 A Separação Inegociável: Aceite de Entrada vs. Confirmação de Negócio
Para evitar o "teatro de maturidade" na observabilidade, este SDD estabelece a separação estrita entre [65, 70]:
* **Aceite HTTP 202 (`RECEIVED`)**: Garantia de que a intenção de crédito foi persistida de forma atômica e durável na borda (< 100 ms) [70, 206]. Não representa aprovação financeira nem averbação [85].
* **Efetivação de Negócio (`CONFIRMED`)**: Confirmação final de averbação e reserva de margem junto ao órgão pagador, processada assincronamente a estritamente **180 TPS** [85, 206].

---

## 2. A Tríade de Níveis de Serviço (SLI, SLO, SLA) & Error Budget Policy

Alinhado aos fundamentos do **Google SRE Book** [225] e aos guias de **Performance Testing da AWS** [200], a confiabilidade da plataforma é dividida em duas jornadas operacionais independentes [70, 89].

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│ JORNADA 1: Borda de Ingestão (Síncrona / Absorção do Choque)                                    │
├──────────────────────────┬──────────────────────────────────────────┬───────────────────────────┤
│ Métrica / SLI            │ Objetivo (SLO)                           │ Janela de Avaliação       │
├──────────────────────────┼──────────────────────────────────────────┼───────────────────────────┤
│ Taxa de Aceite Durável   │ ≥ 99,9% de requisições elegíveis 202     │ Janela de 5 min (Pico)    │
│ Latência de Borda (p95)  │ p95 < 100 ms e p99 < 200 ms              │ Janela de 5 min (Pico)    │
│ Deduplicação Atômica     │ 100% das duplicatas rejeitadas em <15 ms │ Contínua                  │
└──────────────────────────┴──────────────────────────────────────────┴───────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│ JORNADA 2: Processamento Assíncrono & Legado (Back-Office / Outbox)                             │
├──────────────────────────┬──────────────────────────────────────────┬───────────────────────────┤
│ Métrica / SLI            │ Objetivo (SLO)                           │ Janela de Avaliação       │
├──────────────────────────┼──────────────────────────────────────────┼───────────────────────────┤
│ Effective Drain Time     │ Ultra-Fast: <2 min (95%) | Std: <45 min │ Janela móvel de 1 hora    │
│ Contenção do Legado      │ 100% das chamadas ≤ 180 TPS              │ Janela deslizante de 1s   │
│ Frescor da Fila (Lag)    │ Oldest Message Age < 60 minutos          │ Contínua                  │
└──────────────────────────┴──────────────────────────────────────────┴───────────────────────────┘
```

### 2.1 Especificação Detalhada dos SLIs e SLOs

#### SLI 1.1 — Taxa de Aceite Durável na Borda
* **Definição Matemática**:
  $$	ext{SLI}_{	ext{ingestion}} = rac{\sum 	ext{http\_server\_requests\_seconds\_count}\{status="202", outcome="RECEIVED"\}}{\sum 	ext{http\_server\_requests\_seconds\_count}\{	ext{eligible}=true\}}$$
* **SLO**: **99,9%** de aceite durável durante a janela de pico de 5 minutos a 10.000 TPS [206].
* **Error Budget**: **0,1%** de falhas permitidas na borda (3.000 requisições em um lote de 3 milhões) [89].

#### SLI 1.2 — Latência de Resposta de Entrada (p95 / p99)
* **Definição Matemática**: Percentil 95 do tempo total de execução da rota `POST /v1/proposals`.
* **SLO**: **p95 < 100 ms** e **p99 < 200 ms** [206].

#### SLI 1.3 — Deduplicação por Business Fingerprint
* **Definição Matemática**:
  $$	ext{SLI}_{	ext{dedup}} = rac{\sum 	ext{proposals\_deduplicated\_total}\{status="DUPLICATE\_REJECTED"\}}{\sum 	ext{proposals\_duplicate\_attempts\_total}}$$
* **SLO**: **100%** de descarte de tentativas idênticas em **< 15 ms**, sem gerar eventos no SQS [148, 150].

#### SLI 2.1 — Latência de Finalização por Faixa (Effective Drain Time)
* **Definição Matemática**: Tempo decorrido entre a gravação `RECEIVED` na Inbox e o estado terminal (`CONFIRMED` ou `REJECTED`) no Aurora PostgreSQL [85].
* **SLO**:
  * **Ultra Fast / Fast Lane (Score $\ge 50$)**: **95%** processado em **< 2 minutos**.
  * **Standard Lane (Score $< 50$)**: **90%** processado em **< 45 minutos** (garantido por algoritmo anti-starvation $lpha \cdot \Delta t$) [351].

#### SLI 2.2 — Contenção Estrita de Saturação do Legado
* **Definição Matemática**: Vazão instantânea enviada ao Core Banking Legado (`legacy_request_throughput`) [98].
* **SLO**: **$\le 180 	ext{ TPS}$ em 100% das janelas de 1 segundo** [206].

---

### 2.2 Política Realista de Alertas e Alívio do PagerDuty

Para evitar a *alert fatigue* (fadiga de alarmes por falsos positivos de madrugada) sem simular maturidade fictícia, a operação adota **7 Regras Estáticas de Borda com Trava de Tráfego Mínimo** [107, 109]:

```yaml
# Trecho de Configuração Prometheus: consignado-sre.yml
groups:
  - name: consignado-sre-alerts
    rules:
      - alert: ConsignadoIngestionErrors
        expr: (sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))) > 0.05 and sum(increase(http_server_requests_seconds_count[5m])) >= 100
        for: 5m
        labels:
          severity: critical
          notification: sre
        annotations:
          summary: "Taxa de erro 5xx na borda acima de 5% com volume mínimo de 100 req/5m"

      - alert: ConsignadoLegacyUnavailable
        expr: worker_legacy_circuit_state == 2 and worker_queue_depth > 0
        for: 2m
        labels:
          severity: critical
          notification: sre
        annotations:
          summary: "Circuit Breaker do Legado Aberto com mensagens pendentes na fila"
```

* **Regra do Volume Mínimo**: Testes curtos de fumaça (ex: 10 requisições de QA) não atingem o piso de 100 requisições em 5 minutos, impedindo disparos desnecessários no PagerDuty [109].
* **Transição para Burn Rate**: A migração para *Multi-Window Multi-Burn-Rate Alerting* baseada na queima do Error Budget é um objetivo para o próximo ciclo de produção após a consolidação da linha de base de tráfego [113].

---

## 3. Arquitetura do Sistema e Decomposição em 4 Camadas

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│ CAMADA 1: Borda & Ingestão (Java 21 / Virtual Threads + AWS WAF/ALB)                            │
│ - Validação WAF/ALB e sanitização de schema JSON                                                │
│ - Filtro Fast-Fail em Memória (< 5 ms): CPF, Trava DDB 90d INSS, Teto 84 parcelas               │
└──────────────────────────────────────────────┬──────────────────────────────────────────────────┘
                                               │
                                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│ CAMADA 2: Deduplicação & Inbox Durável (Amazon DynamoDB Single-Table)                            │
│ - Hash HMAC-SHA256 (BusinessFingerprint) acoplado ao AWS KMS                                   │
│ - Write Sharding (Partition Salting): PK = HASH#<fingerprint>#PARTITION_(i % 10)                 │
│ - Escrita Condicional Atômica: attribute_not_exists(PK)                                         │
└──────────────────────────────────────────────┬──────────────────────────────────────────────────┘
                                               │
                                               ▼ (CDC / DynamoDB Streams)
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│ CAMADA 3: Despacho Confiável & Enfileiramento QoS (AWS Lambda Dispatcher + Amazon SQS)          │
│ - DynamoDB Streams aciona Lambda Dispatcher (Garantia de No-Message-Loss)                        │
│ - Algoritmo Dynamic Score QoS (VGV, Rating VIP, Risco) + Aging Factor (Anti-Starvation)        │
│ - Roteamento para SQS: sqs-fast-lane-queue, sqs-standard-lane-queue, sqs-dlq                    │
└──────────────────────────────────────────────┬──────────────────────────────────────────────────┘
                                               │
                                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│ CAMADA 4: Worker Fleet & Processamento Cadenciado (ECS Fargate + Resilience4j)                   │
│ - Consumers Spring Boot 3.3 com Virtual Threads                                                 │
│ - RateLimiter / Capacity Budgeting rígido: Fast Lane (126 TPS) + Standard Lane (54 TPS) = 180 TPS│
│ - Transactional Outbox + Persistence State no Amazon Aurora PostgreSQL                          │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 3.1 Camada 1: Borda e Filtro Fast-Fail em Memória (< 5 ms)
Antes de acionar a camada NoSQL, a `Ingestion API` (Java 21) executa 5 Guardrails em memória via cache Caffeine local [206]:
1. **Guardrail 1**: Validação da assinatura do token JWT do parceiro.
2. **Guardrail 2**: Algoritmo do dígito verificador e sanitização de CPF.
3. **Guardrail 3 (IN INSS 138/2022 / IN 172/2024)**: Consulta de trava temporária de 90 dias da DDB (Data de Despacho do Benefício) para bancos não-originadores [334].
4. **Guardrail 4 (MP 1.355/2026 & Portaria MTE 240/2026)**: Validação de teto de 84 parcelas e limites de margem (45% total: 35% empréstimo, 5% RMC, 5% RCC) [49, 334].
5. **Guardrail 5 (Autorregulação Febraban/ABBC)**: Verificação de bloqueio de parceiros suspensos por assédio comercial [309, 334].

Propostas reprovadas nos Guardrails retornam em **< 5 ms** com status `HTTP 422 Unprocessable Entity` em formato **RFC 7807 Problem Details**, sem consumir gravação no DynamoDB ou espaço no SQS [335].

---

### 3.2 Camada 2: Smart Idempotency por HMAC-SHA256 & Write Sharding

To address duplicate requests without relying solely on header UUIDs, the system generates a **Business Fingerprint** using **HMAC-SHA256** backed by AWS KMS:

$$	ext{BusinessFingerprint} = 	ext{HMAC-SHA256}(K_{	ext{KMS}}, 	ext{CPF}_{	ext{norm}} \mid 	ext{AgreementType} \mid 	ext{Amount}_{	ext{norm}} \mid 	ext{Installments} \mid 	ext{DateUTC})$$

#### Tabela de Normalização Canônica Rígida

| Campo | Entrada Exemplo | Formato Canônico | Regra de Normalização |
| :--- | :--- | :--- | :--- |
| **CPF** | `"123.456.789-01"` | `"12345678901"` | Remoção de caracteres não numéricos (`\D`). Exatamente 11 dígitos. |
| **AgreementType**| `"inss "` | `"INSS"` | Uppercase, trim. Validação contra Enum (`INSS`, `ESOCIAL_CLT`, `SIAPE_PUBLICO`). |
| **Amount** | `15000` ou `15000.0` | `"15000.00"` | Formatação via `BigDecimal` escala 2, arredondamento `HALF_EVEN`. Ponto `.`. |
| **Installments** | `84` ou `"084"` | `"84"` | Valor inteiro sem zeros à esquerda. |
| **DateUTC** | `"2026-10-14T08:00:00Z"` | `"2026-10-14"` | Data civil contratual ISO em UTC (`YYYY-MM-DD`). |

#### Tabela DynamoDB `DurableInbox` & Write Sharding (Partition Salting)
Para absorver 10.000 TPS de gravação sem sofrer *Hot Partition Throttling* (limite físico de 1.000 WCU por partição do DynamoDB) [310, 311]:

$$	ext{PartitionSuffix} = 	ext{abs}(	ext{hashCode}(	ext{fingerprint})) \pmod{10}$$
$$	ext{PK} = 	ext{HASH}\#	ext{fingerprint}\#	ext{PARTITION\_}	ext{PartitionSuffix}$$

```java
// Execução do PutItem Condicional Atômico na Ingestion API
Map<String, AttributeValue> item = new HashMap<>();
item.put("PK", AttributeValue.builder().s(pk).build());
item.put("SK", AttributeValue.builder().s("METADATA").build());
item.put("proposalId", AttributeValue.builder().s(proposalId).build());
item.put("status", AttributeValue.builder().s("RECEIVED").build());
item.put("ttl", AttributeValue.builder().n(String.valueOf(ttlEpoch24h)).build());

PutItemRequest request = PutItemRequest.builder()
    .tableName("DurableInbox")
    .item(item)
    .conditionExpression("attribute_not_exists(PK)") // Trava atômica NoSQL
    .build();
```

---

### 3.3 Camada 3: Despacho Confiável (CDC) & Algoritmo Dynamic Score QoS

#### Prevenção de Mensagens Órfãs (CDC via DynamoDB Streams)
A `Ingestion API` grava **apenas** no DynamoDB. A publicação na fila SQS é realizada de forma reativa pelo **AWS Lambda Dispatcher** ao consumir o log do DynamoDB Streams, garantindo o padrão *Transactional Outbox / CDC* sem risco de mensagens perdidas [146, 147, 207].

#### Algoritmo Dynamic Score QoS & Anti-Starvation
O Dispatcher calcula o score de prioridade de cada proposta ($0 \dots 100$) em < 3 ms:

$$	ext{Score}_{	ext{base}} = (0{,}40 \cdot 	ext{VGV}_{	ext{norm}}) + (0{,}35 \cdot 	ext{Rating}_{	ext{VIP}}) + (0{,}25 \cdot 	ext{RiscoConcorrencia} \cdot M_{	ext{OpenFinance}})$$

* **Multiplicador de Portabilidade (Res. CMN 4292 / CMN 5265)**: $M_{	ext{OpenFinance}} = 2{,}0	imes$ para retenção de clientes.
* **Mecanismo Anti-Starvation (Aging Factor)**:
  $$	ext{Score}_{	ext{efetivo}}(t) = 	ext{Score}_{	ext{base}} + (lpha \cdot \Delta t), \quad 	ext{com } lpha = 0{,}10 	ext{ pts/segundo}$$
  Propostas na `Standard Lane` promovidas automaticamente para a `Fast Lane` se retidas por mais de 10 minutos [351].

---

### 3.4 Camada 4: Worker Fleet, Capacity Budgeting & Tratamento de Timeouts

O cluster ECS Fargate consome as filas mantendo a contenção estrita do legado em **180 TPS** [134, 206]:
* **Fast Lane Allocation**: 70% da capacidade (**126 TPS**) [134, 209].
* **Standard Lane Allocation**: 30% da capacidade (**54 TPS**) [134, 209].

```java
// Configuração do RateLimiter Resilience4j no Worker Fleet
RateLimiterConfig config = RateLimiterConfig.custom()
    .limitRefreshPeriod(Duration.ofSeconds(1))
    .limitForPeriod(126) // Faixa Fast Lane
    .timeoutDuration(Duration.ofMillis(500))
    .build();
RateLimiterRegistry registry = RateLimiterRegistry.of(config);
```

#### Tratamento da Ambiguidade de Timeout no NoSQL (HTTP 503 com Escrita Persistida)
Quando a gravação no DynamoDB estoura o timeout da API (ex: `ApiCallTimeoutException` em 500 ms) e a API responde `HTTP 503`, mas a gravação é concluída 20 ms depois [115]:
1. O parceiro deve retransmitir a chamada preservando o mesmo **Business Fingerprint**.
2. A Ingestion API baterá no registo gravado no DynamoDB e responderá com `HTTP 202 Accepted` e cabeçalho `X-Idempotency-Status: DUPLICATE_ACCEPTED`, recuperando o fluxo sem duplicar a operação financeira [69, 85].

---

## 4. Telemetria, PromQL e Observabilidade da Observabilidade

### 4.1 Consultas PromQL Oficiais para o Grafana

#### 1. Taxa de Aceite Durável na Borda (SLO 1.1)
```promql
sum(rate(http_server_requests_seconds_count{status="202", outcome="RECEIVED"}[5m]))
/
sum(rate(http_server_requests_seconds_count[5m]))
```

#### 2. Latência p95 de Ingestão (SLO 1.2)
```promql
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))
```

#### 3. Divergência VGV Recebido vs. VGV Confirmado (Detector de Represamento)
```promql
sum(increase(business_vgv_received_brl_sum[5m])) - sum(increase(business_vgv_brl_sum[5m]))
```

#### 4. Contenção de Saturação do Legado (SLO 2.2 - Teto 180 TPS)
```promql
sum(rate(worker_legacy_call_seconds_count[1s])) <= 180
```

---

### 4.2 Governança LGPD e Banimento de Alta Cardinalidade
* **Banimento de PII em Metrics**: É estritamente proibido utilizar CPF, e-mail, nomes ou tokens como labels de métricas do Prometheus (previne explosão do TSDB) [86, 100]. Dimensions permitidas: `service`, `env`, `lane`, `outcome`, `convenio`.
* **Data Scrubbing nos Logs**: Uso do customizador `PiiRedactingJsonMembersCustomizer` para aplicar mascaramento Regex (`XXX.456.789-XX`) antes da emissão do log JSON/ECS para o stdout [125].

---

### 4.3 Rastreabilidade Distribuída Real (OpenTelemetry & W3C Trace Context)

Para garantir rastreabilidade ponta a ponta sem recorrer a *fallbacks* de UUID no MDC [66, 74]:
1. A Ingestion API injeta o cabeçalho W3C `traceparent` (`00-{trace_id}-{span_id}-01`) e `tracestate` nos atributos da mensagem SQS (`MessageAttributes`) ao publicar [66].
2. O Worker Fleet extrai o contexto W3C usando o `W3CTraceContextPropagator` oficial do SDK Java da OTel, declarando `SpanKind.CONSUMER` na leitura da fila e `SpanKind.CLIENT` na chamada ao legado [347].

---

## 5. Matriz de Rastreabilidade de Requisitos (SRS / SDD Matrix)

| Código Requisito (SRS) | Componente SDD Responsável | Mecanismo de Validação | Status DoD |
| :--- | :--- | :--- | :--- |
| **REQ-FUN-001 (Ingestão High-TPS)** | `Ingestion-API-Service` (Java 21) | Carga k6 a 10.000 TPS por 5 minutos (p95 < 100 ms) | **APROVADO** [206] |
| **REQ-FUN-002 (Idempotência)** | `DurableInboxRepository` + HMAC KMS | Injeção de 50.000 duplicatas resultando em 1 escrita | **APROVADO** [211] |
| **REQ-FUN-003 (Proteção Legado)** | Worker Fleet + Resilience4j RateLimiter | Medição no Mock Legacy $\le 180 	ext{ TPS}$ constante | **APROVADO** [206] |
| **REQ-REG-001 (Margem INSS)** | `FastFailFilterEngine` (Guardrail 4) | Rejeição de parcelas > 84 e margem > 45% em < 5 ms | **APROVADO** [334] |
| **REQ-SRE-001 (SLO Telemetria)** | Prometheus / Alertmanager / Grafana | Validação de 7 regras SRE e canal SNS/SQS | **APROVADO** [107] |

---

## 6. Considerações Finais & Isenção de Escopo

Este documento de design de software foi elaborado com base no repositório de código, testes de carga e especificações regulatórias do ecossistema brasileiro de crédito consignado. 

Toda a volumetria, infraestrutura simulada em EC2 e hipóteses operacionais correspondem a um **cenário de Prova de Conceito (PoC)** [81, 117]. O objetivo deste SDD não é ditar dogmas universais, mas fornecer uma especificação defensável, auditável e pronta para geração automatizada de código e testes via IA (AI-DLC / Spec-Driven Development) [205].
