# Software Design Document (SDD): PoC de Ingestão Massiva e Resiliência para Crédito Consignado

> Nota de escopo — revisão de 16/10/2026: Este documento registra uma especificação ou decisão de arquitetura. Metas, componentes target e declarações de aprovação não comprovam implementação ou homologação da PoC atual. Consulte o [índice documental](../README.md) e as limitações atuais antes de usar este texto como descrição do ambiente.


**Projeto:** Platform Engine — PoC Crédito Consignado  
**Versão:** 1.0.0-SNAPSHOT  
**Data:** 06 de Outubro de 2026  
**Status:** Aprovado para Engenharia (Ready for Engineering)  
**Nível de Maturidade:** Staff / Principal Software Engineer  
**Autor:** Equipe de Arquitetura & Engenharia de Plataforma  

---

## 1. Controle do Documento & Resumo Executivo

### 1.1. Objetivo do Documento
Este Documento de Design de Software (SDD) especifica formalmente a arquitetura, contratos de API, esquemas de persistência, padrões de concorrência, governança de QoS e estratégias de observabilidade para a **PoC de Ingestão Massiva e Resiliência de Crédito Consignado**. 

Este documento serve como a referência técnica definitiva (*Single Source of Truth*) para os times de Engenharia de Software, DevOps/SRE e Arquitetura Corporativa.

### 1.2. Problema de Negócio & Hipóteses de Volumetria
O mercado de crédito consignado brasileiro atinge mais de **R$ 484 bilhões** em carteira ativa (servidores públicos, INSS e consignado privado CLT/eSocial). Na "virada de folha" mensal de margem consignável, o tráfego do banco sofre um pico brutal gerado por robôs e scripts de parceiros/correspondentes bancários, saltando de **50 TPS para 10.000 TPS** às 00:01.

O Core Banking legado atua como um gargalo físico intransponível com capacidade segura limitada a **180 TPS**. A arquitetura síncrona atual colapsa sob esse pico, resultando em exaustão de conexões, downtime operacional e perda de originação para a concorrência.

### 1.3. Requisitos Não-Funcionais Principais (NFRs)
* **Pico Extremo de Carga:** 10.000 TPS mantidos por 5 minutos (~3.000.000 de requisições).
* **SLA da Borda (Ingestão):** Resposta `HTTP 202 Accepted` com p95 < 100 ms e p99 < 200 ms.
* **Teto do Legado (Capacity Limit):** Vazão mantida rigorosamente em **<= 180 TPS**.
* **Garantia de Idempotência:** 100% de prevenção contra duplicidade no processamento e averbação.
* **Governança Multi-Tenant:** Rate limiting isolado por parceiro e conformidade com a autorregulação Febraban/ABBC.

---

## 2. Visão Geral da Arquitetura & Topologia

A solução adota o padrão de **Desacoplamento Temporal e Borda de Absorção Absoluta**. Em vez de tentar escalar síncronamente o Core Banking legado, a plataforma absorve a demanda na nuvem, garante durabilidade e idempotência imediatas, e alimenta o legado em formato "conta-gotas" respeitando seu limite físico de 180 TPS.

```
[ Robôs dos Parceiros / Fintechs ]
                 │
                 │ (10.000 TPS - POST /v1/proposals)
                 ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 1. CAMADA DE BORDA & INGESTÃO (Multi-Tenant & SLA < 100ms)             │
│    AWS WAF v2 (Rate Limiting) ──► ALB ──► Ingestion Fleet               │
│                                           (Java 21 / Virtual Threads)  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (Escrita Condicional Atômica)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 2. BUFFER DURÁVEL DE ALTA PERFORMANCE (Durable Inbox)                  │
│    Amazon DynamoDB (Single-Table / Conditional Check: attribute_not_exists) │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (DynamoDB Streams)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 3. DISPATCHER & ROTEAMENTO INTELIGENTE (Quality of Service - QoS)      │
│    AWS Lambda Dispatcher                                               │
│    ├── Fast Lane SQS (70% Budget = 126 TPS) ──► LTV/VIP & Open Finance │
│    └── Standard Lane SQS (30% Budget = 54 TPS) ──► Demais Operações    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (Polling Reativo com Virtual Threads)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 4. WORKER FLEET & CAPACITY BUDGETING                                   │
│    ECS Fargate Workers (Java 21 / Spring Boot 3.x)                     │
│    └── Resilience4j Rate Limiter (Max 180 TPS Global)                  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (Chamadas HTTP / REST Protegidas)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ 5. SISTEMA LEGADO & PERSISTÊNCIA RELACIONAL (Outbox Pattern)           │
│    Mock Legacy (180 TPS max) ──► Amazon Aurora PostgreSQL              │
│                                   (Tabelas: proposal & proposal_event) │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Especificação Detalhada dos Componentes

### 3.1. Camada de Borda (Ingestion Fleet)
* **Tecnologia:** Java 21, Spring Boot 3.x, Tomcat embarcado com Virtual Threads (`spring.threads.virtual.enabled=true`).
* **Infraestrutura:** AWS WAF v2 + Application Load Balancer (ALB) + AWS ECS Fargate.
* **Responsabilidade:** Validação de schema JSON, autenticação JWT, verificação de cota multi-tenant por parceiro (`X-Tenant-ID`), gravação atômica no DynamoDB e emissão de resposta `HTTP 202 Accepted` com `Trace-ID` de observabilidade.
* **Comportamento sob Duplicidade:** Se receber uma requisição com `Idempotency-Key` já existente, a gravação condicional no DynamoDB falha (`ConditionalCheckFailedException`), e a API retorna `HTTP 202 Accepted` silenciosamente com o mesmo `Trace-ID`, sem reprocessar.

### 3.2. Buffer Durável (Amazon DynamoDB - Durable Inbox)
* **Modelo de Dados:** Tabela Single-Table `DurableInbox`.
* **Partition Key (PK):** `idempotencyKey` (String / UUID v4).
* **Escrita Condicional:** `ConditionExpression: "attribute_not_exists(idempotencyKey)"`.
* **Configuração de Throughput:** On-Demand com suporte a rajadas automáticas ou Provisioned com Auto-Scaling.
* **Retenção (TTL):** Atributo `ttl` definido para expiração automática em 7 dias após a ingestão.

### 3.3. Roteamento Inteligente & QoS (AWS Lambda Dispatcher & Amazon SQS)
* **Gatilho:** AWS Lambda ativado via **DynamoDB Streams**.
* **Algoritmo de Roteamento:**
  1. Identifica o convênio (INSS, eSocial/CLT, Servidor Público).
  2. Verifica flag de prioridade (ex: Portabilidade via Open Finance Res. CMN 15/2025, Alto LTV ou Parceiro VIP).
  3. Encamufla o evento para a **`sqs-fast-lane-queue`** (se prioritário) ou para a **`sqs-standard-lane-queue`** (demais propostas).
* **Filas Provisionadas:**
  * `sqs-fast-lane-queue`: Prioridade de consumo.
  * `sqs-standard-lane-queue`: Consumo padrão.
  * `sqs-dlq`: Dead Letter Queue para mensagens corrompidas após 3 tentativas incompletas.

### 3.4. Worker Fleet & Capacity Budgeting
* **Tecnologia:** Java 21 + Spring Boot 3.x no AWS ECS Fargate.
* **Mecanismo de Concorrência:** Virtual Threads para polling assíncrono não-bloqueante do SQS.
* **Resilience4j Rate Limiter:** Parâmetros configurados para garantir que a frota global de workers nunca ultrapasse o orçamento do legado:
  * **Global Legacy Limit:** 180 TPS.
  * **Fast Lane Allocation:** 70% da capacidade (até 126 TPS).
  * **Standard Lane Allocation:** 30% da capacidade (até 54 TPS).
* **Resilience4j Circuit Breaker:** Se o Mock Legado começar a responder com latência > 2.000 ms ou retornar erros 5xx acima de 10%, o Circuit Breaker abre, interrompendo chamadas downstream e permitindo a autorecuperação do legado sem *retry storms*.

### 3.5. Estado Relacional & Outbox Pattern (Amazon Aurora PostgreSQL)
* **Instância:** Amazon Aurora PostgreSQL Serverless v2.
* **Tabelas Principais:**
  * `proposal`: Armazena o estado consolidado da proposta (`id`, `customer_id`, `partner_id`, `status`, `amount`, `installments`, `created_at`, `updated_at`).
  * `proposal_event`: Padrão **Transactional Outbox** para registrar histórico auditável e publicação de eventos de integração.

---

## 4. Esquemas de Dados e Contratos de API

### 4.1. Contrato OpenAPI da Ingestion API (`POST /v1/proposals`)

```yaml
openapi: 3.0.3
info:
  title: Ingestion API - Crédito Consignado
  version: 1.0.0
paths:
  /v1/proposals:
    post:
      summary: Ingestão síncrona durável de proposta de crédito
      headers:
        Authorization:
          schema:
            type: string
          required: true
          description: Token Bearer JWT
        Idempotency-Key:
          schema:
            type: string
            format: uuid
          required: true
          description: Chave única de idempotência gerada pelo parceiro
        X-Tenant-ID:
          schema:
            type: string
          required: true
          description: Identificador do correspondente bancário / parceiro
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ProposalRequest'
      responses:
        '202':
          description: Proposta aceita e armazenada com sucesso para processamento
          headers:
            X-Trace-ID:
              schema:
                type: string
          content:
            application/json:
              schema:
                type: object
                properties:
                  status:
                    type: string
                    example: "ACCEPTED"
                  idempotencyKey:
                    type: string
                    example: "e3b0c442-98fc-11ee-b9d1-0242ac120002"
                  receivedAt:
                    type: integer
                    example: 1772841600
        '400':
          description: Payload ou headers inválidos
        '429':
          description: Rate limit do parceiro excedido na borda

components:
  schemas:
    ProposalRequest:
      type: object
      required:
        - customerCpf
        - conveniosCode
        - amount
        - installments
      properties:
        customerCpf:
          type: string
          example: "12345678901"
        conveniosCode:
          type: string
          enum: [INSS, ESOCIAL_CLT, SIAPE_PUBLICO]
          example: "INSS"
        isPortability:
          type: boolean
          example: false
        amount:
          type: number
          format: double
          example: 15000.50
        installments:
          type: integer
          example: 84
```

### 4.2. Esquema da Tabela `DurableInbox` (Amazon DynamoDB)

| Atributo | Tipo | Função / Descrição |
| :--- | :--- | :--- |
| **`idempotencyKey`** | String (PK) | Partition Key atômica gerada pelo chamador (UUID) |
| **`tenantId`** | String | Código do parceiro / correspondente bancário |
| **`convenioType`** | String | Categoria do convênio (`INSS`, `ESOCIAL_CLT`, `SIAPE`) |
| **`isPriority`** | Boolean | Flag de alta prioridade (Portabilidade Open Finance / VIP) |
| **`payload`** | String (JSON) | JSON serializado e comprimido da proposta |
| **`status`** | String | Status (`RECEIVED`, `DISPATCHED`, `FAILED`) |
| **`createdAt`** | Number | Timestamp Epoch da ingestão |
| **`ttl`** | Number | Timestamp Epoch para expiração automática (7 dias) |

### 4.3. Script DDL da Persistência Relacional (Aurora PostgreSQL)

```sql
CREATE TABLE proposal (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(64) UNIQUE NOT NULL,
    tenant_id VARCHAR(32) NOT NULL,
    customer_cpf VARCHAR(11) NOT NULL,
    convenio_type VARCHAR(20) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    installments INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE proposal_event (
    event_id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES proposal(id),
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_proposal_tenant ON proposal(tenant_id);
CREATE INDEX idx_proposal_status ON proposal(status);
```

---

## 5. Telemetria, Observabilidade & Alertas

A instrumentação da base de código adota o **Micrometer** exportando métricas nativas para **Prometheus** e painéis no **Grafana**.

### 5.1. Métricas Principais
* **`ingestion.accepted.rate`**: Taxa de requisições absorvidas com sucesso na borda (`HTTP 202`).
* **`ingestion.http429.rate`**: Taxa de rejeição na borda por *Rate Limit* de parceiros anômalos.
* **`net_backlog_growth`**: Taxa de Chegada (\\(\lambda\\)) - Taxa de Processamento (\\(\mu\\)). Valor > 0 indica acúmulo temporário na fila.
* **`effective_drain_time`**: Cálculo em tempo real de recuperação: \\(\text{QueueDepth} / \text{ProcessingRate}\\).
* **`legacy.request.throughput`**: Vazão real entregue ao Mock Legado (deve se manter estritamente <= 180 TPS).
* **`legacy.request.latency`**: Histogramas de latência p50, p95 e p99 do sistema legado.

### 5.2. Painéis do Grafana
1. **Painel de Borda (Ingestion Health):** Exibe o gráfico de entrada até 10.000 TPS, código de resposta 202 e latência p95 < 100 ms.
2. **Painel de Proteção Downstream:** Exibe a linha do legado cravada perfeitamente em 180 TPS, demonstrando o efeito "conta-gotas".
3. **Painel de Backlog & Drenagem:** Exibe o volume total armazenado no SQS e o tempo dinâmico estimado para esvaziamento completo.

---

## 6. Critérios de Aceitação para Homologação (Definition of Done - DoD)

A PoC estará tecnicamente aprovada quando o script automatizado de testes em **k6** certificar os seguintes itens:

1. **Teste de Carga Sustentada:** Injeção de **10.000 TPS durante 5 minutos** (3.000.000 de requisições) com a Ingestion API mantendo resposta `HTTP 202` em **p95 < 100 ms** e disponibilidade > 99,9%.
2. **Proteção Total do Legado:** O Mock Legado não pode registrar tráfego superior a **180 TPS** em nenhuma janela de 1 segundo.
3. **Zero Duplicação Transacional:** Um disparo simultâneo de **50.000 requisições** contendo a mesma `Idempotency-Key` deve resultar em exatamente **1 registro final** no Aurora PostgreSQL e zero averbações duplicadas.
4. **Resiliência Multi-Tenant:** Um parceiro tentando enviar carga acima de sua cota individual deve receber `HTTP 429` sem afetar a ingestão dos demais parceiros.
5. **Drenagem Autónoma do Backlog:** Após o cessar do disparo de 10.000 TPS, os workers ECS devem continuar consumindo as filas SQS de forma priorizada (70% Fast / 30% Standard) até a total liquidação do backlog sem intervenção manual.
