### Especificação de Sistema Orientada a IA (AI-DLC / Spec-Driven Development) - Versão Enterprise
**Projeto:** PoC de Ingestão Massiva, Resiliência e Governança Multi-Tenant — Crédito Consignado
**Status:** Ready for AI Generation | **Nível de Maturidade:** Staff / Principal Engineering
Este documento fornece as especificações arquiteturais, requisitos não-funcionais (NFRs), esquemas de dados, contratos de API e suíte de testes automatizados para a geração de código e infraestrutura via *AI-Driven Development Lifecycle* (IA-DLC).

--------------------------------------------------------------------------------

#### 1. Contexto e Hipóteses de Volumetria (NFRs)
A arquitetura deve desacoplar a taxa de entrada da capacidade de processamento do legado, incorporando suporte multi-tenant e priorização de portabilidade Open Finance.
*   **Tráfego Normal:** 50 TPS.
*   **Pico Extremo de Teste (Virada de Folha):** 10.000 TPS.
*   **Duração do Pico:** 5 minutos.
*   **Volume Total no Pico:** Aproximadamente 3.000.000 de requisições.
*   **Capacidade Nominal do Legado:** 200 TPS.
*   **Target Máximo Operacional Seguro:** 180 TPS.
*   **SLA de Ingestão (Borda):** Resposta HTTP 202 com p95 < 100 ms e p99 < 200 ms.
*   **Multi-Tenancy & Rate Limiting:** Suporte a múltiplos parceiros/correspondentes com cotas dinâmicas por tenant.

--------------------------------------------------------------------------------

#### 2. Topologia de Componentes para Infraestrutura como Código (Terraform)

##### 2.1. Camada de Borda, Governança e Ingestão
*   **AWS WAF v2:** Regras de rate limiting por IP, mitigação de robôs e Throttling por Tenant ID no header.
*   **Application Load Balancer (ALB):** Roteamento seguro HTTPS (TLS 1.3) para a Ingestion Fleet.
*   **Ingestion Fleet:** Cluster ECS/Fargate executando microsserviços Java 21 (Spring Boot 3.3). Responsável por autenticação JWT, validação de schema, verificação de idempotência no DynamoDB e emissão de eventos OpenTelemetry.

##### 2.2. Buffer Durável, Streams e Roteamento QoS
*   **Amazon DynamoDB (Durable Inbox):** Tabela Single-Table Design para armazenamento atômico e deduplicação FinOps.
*   **AWS Lambda (Dispatcher):** Função ativada por DynamoDB Streams com cálculo de prioridade (LTV, rentabilidade, convênio e Fast-Track Open Finance).
*   **Amazon SQS:**
    *   `sqs-fast-lane-queue`: Propostas prioritárias, alta margem e portabilidade Open Finance.
    *   `sqs-standard-lane-queue`: Operações padrão.
    *   `sqs-dlq`: Fila de mensagens mortas com retenção de 14 dias.

##### 2.3. Camada de Processamento, Proteção e Estado
*   **Worker Fleet:** Cluster ECS/Fargate consumindo SQS com Java 21 Virtual Threads e controle de vazão via Resilience4j Rate Limiter (limite global de 180 TPS).
*   **Amazon Aurora PostgreSQL:** Banco relacional gerenciado para o estado do contrato e tabela `proposal_event` (padrão Transactional Outbox).

--------------------------------------------------------------------------------

#### 3. Especificação de Dados e Persistência

##### 3.1. Durable Inbox (DynamoDB)
*   **Tabela:** `DurableInbox`
*   **Partition Key (PK):** `idempotencyKey` (String / UUID).
*   **Mecanismo de Idempotência:** Gravado estritamente com `ConditionExpression: "attribute_not_exists(idempotencyKey)"`.
*   **Atributos:**
    *   `payload` (String / JSON)
    *   `tenantId` (String)
    *   `agreementType` (INSS, ESOCIAL, ECONSIG, OPEN_FINANCE)
    *   `priorityScore` (Number)
    *   `status` (String: RECEIVED, DISPATCHED, FAILED)
    *   `createdAt` (Number / Epoch)
    *   `ttl` (Number / Epoch Expiration)

##### 3.2. Estado Relacional (Aurora PostgreSQL)
*   **Tabela `proposal`:** `id` (UUID), `customer_id` (String), `partner_id` (String), `agreement_type` (String), `status` (String), `amount` (Decimal), `installments` (Integer), `created_at` (Timestamp), `updated_at` (Timestamp).
*   **Tabela `proposal_event` (Outbox):** `event_id` (UUID), `proposal_id` (UUID), `event_type` (String), `payload` (JSONB), `created_at` (Timestamp).

--------------------------------------------------------------------------------

#### 4. Contratos de API e Regras da Máquina de Estado

##### 4.1. Endpoint de Ingestão
**POST** `/v1/proposals`
**Headers Obrigatórios:**
*   `Authorization`: `Bearer <JWT>`
*   `Idempotency-Key`: `<UUID>`
*   `X-Tenant-ID`: `<String>`

**Fluxo de Execução Exigido:**
1. Validação de formato do payload e verificação de cotas por tenant.
2. Inserção condicional no DynamoDB com a `Idempotency-Key`.
3. Em caso de sucesso: Retornar `HTTP 202 Accepted` com `{"status": "RECEIVED", "tracingId": "<UUID>"}` em < 100 ms.
4. Em caso de duplicidade (`ConditionalCheckFailedException`): Retornar `HTTP 202 Accepted` silenciosamente com `{"status": "DUPLICATE_ACCEPTED", "tracingId": "<UUID>"}` sem reenfileirar.

##### 4.2. Máquina de QoS (Capacity Budgeting nos Workers)
O Worker Fleet deve consumir as filas SQS respeitando a divisão do orçamento global de 180 TPS:
*   **Fast Lane Budget (70% = 126 TPS):** Destinado a contratos de alta margem, seguros acoplados e portabilidade Open Finance.
*   **Standard Lane Budget (30% = 54 TPS):** Garantia de vazão para as demais operações, evitando *starvation*.

--------------------------------------------------------------------------------

#### 5. Instrumentação e Telemetria (Micrometer / Grafana)
*   **Métricas de Ingestão:** `ingestion.accepted.rate` e `ingestion.throttled.tenant.rate`.
*   **Métricas de Fila:** `net_backlog_growth` (taxa de chegada $\lambda$ - taxa de consumo $\mu$) e `effective_drain_time` ($\text{QueueDepth} / \text{EffectiveRate}$).
*   **Métricas de Proteção:** `legacy.request.throughput` (travado em <= 180 TPS) e `legacy.request.latency` (p50, p95, p99).

--------------------------------------------------------------------------------

#### 6. Suíte de Testes de Carga (k6 Script Specification)
O IA-DLC deve gerar um script k6 contendo os seguintes cenários:
1.  **Stress Test:** 10.000 TPS durante 5 minutos contra o endpoint `/v1/proposals`.
2.  **Validação de Idempotência:** Injeção concorrente de 500 requisições com a mesma `Idempotency-Key` verificando exatamente 1 inserção no banco downstream.
3.  **Tenant Throttling Test:** Simulação de um parceiro anômalo estourando sua cota e recebendo `HTTP 429` enquanto parceiros normais mantêm `HTTP 202`.
4.  **Drenagem Pós-Pico:** Verificação da continuidade do consumo dos workers a 180 TPS até o esvaziamento total do backlog.
