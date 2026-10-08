### Blueprint de Implementação da PoC: Orquestração de Alta Vazão para Crédito Consignado (Versão Enterprise Multi-Tenant)
**Status:** Aprovado para Desenvolvimento (Ready for Engineering) | **Nível de Maturidade:** Staff / Principal Engineering
**Objetivo:** Materializar a arquitetura de desacoplamento temporal, provando a absorção de 10.000 TPS na borda, governança multi-tenant anti-spam (conforme diretrizes da Febraban/ABBC) e o processamento seguro de 180 TPS no legado, através de um backlog observável, priorizado e compatível com Open Finance e múltiplos convênios (INSS, eSocial/FGTS Digital, eConsig).

--------------------------------------------------------------------------------

#### 1. Stack Tecnológico Definitivo
A fundação técnica foi atualizada para suportar arquitetura multi-tenant, rastreabilidade regulatória e alto desempenho de I/O:
*   **Infraestrutura e Orquestração:**
    *   **Provisionamento:** Terraform (módulos reutilizáveis para AWS).
    *   **Borda & Governança:** AWS WAF v2 (Rate Limiting dinâmico por IP/Tenant) + Application Load Balancer (ALB).
    *   **Compute:** Amazon Elastic Container Service (ECS) Fargate para a Ingestion API e Worker Fleet.
*   **Aplicações e Microsserviços:**
    *   **Linguagem & Runtime:** Java 21 LTS (com Virtual Threads / Project Loom para máxima eficiência I/O-bound).
    *   **Framework:** Spring Boot 3.3+ / GraalVM Native Image (opcional para arranque instantâneo).
    *   **Resiliência & Rate Limiting:** Resilience4j (Rate Limiter por Tenant, Circuit Breaker, Bulkhead).
*   **Mensageria, Buffer e Persistência:**
    *   **Durable Inbox:** Amazon DynamoDB (Single Table Design com Conditional Writes atômicas e TTL).
    *   **Stream & Dispatcher:** AWS Lambda acionado reativamente via DynamoDB Streams.
    *   **Filas de Negócio (QoS):** Amazon SQS (`sqs-fast-lane-queue`, `sqs-standard-lane-queue`, `sqs-dlq`).
    *   **Estado & Auditoria:** Amazon Aurora PostgreSQL (JPA/Hibernate com padrão Transactional Outbox).
*   **Observabilidade & Tracing End-to-End:**
    *   **Métricas:** Prometheus + Micrometer + Pushgateway + Alertmanager.
    *   **Visualização:** Grafana (Dashboards de Net Backlog Growth, Effective Drain Time e SLA por Tenant).
    *   **Tracing & Compliance:** OpenTelemetry / Jaeger (propagação de `traceparent` / Tracing ID único da borda ao legado).

--------------------------------------------------------------------------------

#### 2. Contratos e Arquitetura de Dados (Fase de Ingestão e Governança)
Garantia de idempotência atômica, isolamento multi-tenant e rastreabilidade FinOps.

**Esquema DynamoDB (Tabela `DurableInbox`)**
*   **Partition Key (PK):** `idempotencyKey` (UUID V4 gerado pela aplicação parceira/correspondente).
*   **Atributos:** `payload` (JSON comprimido GZIP/Snappy), `tenantId` (String), `partnerRating` (Number), `agreementType` (INSS, ESOCIAL, ECONSIG, OPEN_FINANCE), `priorityScore` (Number), `status` (RECEIVED, DISPATCHED, FAILED), `createdAt` (Epoch Ms), `ttl` (Time-To-Live de 7 dias).
*   **Operação de Escrita Atômica na Ingestion API:**
```java
PutItemRequest request = PutItemRequest.builder()
    .tableName("DurableInbox")
    .item(itemValues)
    .conditionExpression("attribute_not_exists(idempotencyKey)") // Bloqueio atômico de robôs duplicados
    .build();
```

--------------------------------------------------------------------------------

#### 3. Roteiro de Implementação (Sprints de Engenharia)

##### Sprint 1: Fundação, Mock Legado e Simulador de Convênios
1.  **Mock Legacy:** Aplicação Spring Boot simulando o Core Banking com capacidade máxima travada em 180 TPS. Respostas acima do limite disparam HTTP 503 ou degradação controlada.
2.  **Simulador de Convênios:** Mocks das interfaces Dataprev, eSocial e eConsig/SouGov para validação de margem e averbação.
3.  **Observabilidade Base:** Prometheus e Grafana configurados para monitorar latências p50, p95 e p99.

##### Sprint 2: Borda Multi-Tenant e Absorção Massiva (10.000 TPS)
1.  **Ingestion API (Java 21):** Endpoint `POST /v1/proposals` com validação de schema e suporte ao header `X-Tenant-ID`.
2.  **Rate Limiter por Tenant:** Configuração no AWS WAF e Resilience4j para barrar requisições anômalas de correspondentes específicos (combate a spams e conformidade Febraban).
3.  **Idempotência e Resposta Otimizada:** Gravação no DynamoDB com trava condicional e devolução de `HTTP 202 Accepted` em < 100 ms com Tracing ID.

##### Sprint 3: Roteamento Assíncrono e Fast-Track Open Finance (Dispatcher)
1.  **Provisionamento SQS:** Filas `Fast-Lane`, `Standard-Lane` e `DLQ` provisionadas via Terraform.
2.  **AWS Lambda Dispatcher:** Função ativada pelo DynamoDB Streams. Calcula a pontuação de prioridade com base no LTV, histórico do parceiro e tipo de operação (Fast-Track priorizado para Portabilidade via Open Finance - Resolução CMN/BCB nº 15/2025).

##### Sprint 4: Worker Fleet, Capacity Budgeting (70/30) e Outbox
1.  **Worker Fleet (ECS Fargate):** Consumidores SQS em Java 21 com Virtual Threads.
2.  **Capacity Budgeting:** Orçamento global estrito de 180 TPS dividido em:
    *   **Fast Lane:** Até 126 TPS (70%) para propostas de alta margem, seguros acoplados e portabilidade.
    *   **Standard Lane:** Até 54 TPS (30%) para operações padrão.
3.  **Resilience4j & Transactional Outbox:** Proteção contra falhas no legado e gravação do estado no Aurora PostgreSQL na tabela `proposal_event`.

##### Sprint 5: Dashboard Executivo e Teste de Carga Real
1.  **Telemetria de Negócio no Grafana:** Painéis exibindo *Net Backlog Growth* e *Effective Drain Time*.
2.  **Execução do Script k6:** Teste de estresse com 3.000.000 de requisições em 5 minutos (10.000 TPS) e injeção de 50.000 chaves duplicadas para homologação do DoD.

--------------------------------------------------------------------------------

#### 4. Definição de Pronto (Definition of Done - DoD)
1.  **Injeção Massiva na Borda:** Disparo de 3.000.000 de requisições em 5 minutos (10.000 TPS) respondendo com `HTTP 202` em > 99,9% dos casos e p95 < 100 ms.
2.  **Proteção Rigorosa do Legado:** Vazão do Mock Legado estritamente travada em <= 180 TPS em qualquer janela de 1 segundo.
3.  **Idempotência FinOps:** Injeção de 50.000 requisições duplicadas resultando em exatamente 1 registro processado no PostgreSQL e 0 duplicações de averbação.
4.  **Isolamento Multi-Tenant:** Disparo de tráfego anômalo por um único parceiro resulta em throttling local (`HTTP 429`) sem afetar a vazão dos demais parceiros.
5.  **Drenagem Priorizada do Backlog:** Esvaziamento contínuo das filas mantendo a proporção de 70/30 entre Fast Lane e Standard Lane.
