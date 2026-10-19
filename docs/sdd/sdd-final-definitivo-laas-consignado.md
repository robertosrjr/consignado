# Software Design Document (SDD) Definitivo — Arquitetura Target de Produção e Engine de Resiliência

> Nota de escopo — revisão de 16/10/2026: Este documento registra uma especificação ou decisão de arquitetura. Metas, componentes target e declarações de aprovação não comprovam implementação ou homologação da PoC atual. Consulte o [índice documental](../README.md) e as limitações atuais antes de usar este texto como descrição do ambiente.


**Projeto:** Plataforma LaaS, Agente IA & Engine de Alta Vazão para Crédito Consignado e PME  
**Maturidade Arquitetural:** Staff / Principal Systems Engineer  
**Status:** Approved for Production Roadmap & Architecture Board Review  
**Padrão Documental:** IEEE 1016-2009 & ISO/IEC/IEEE 42010  
**Versão:** 4.0 (Unificação Definitiva: Arquitetura Estagiada Target, Smart Idempotency por Business Hash, Fast-Fail Filter Engine & Especificações de Produção)  

---

## 1. Controle do Documento & Resumo Executivo

### 1.1. Objetivo do Documento
Este Documento de Design de Software (SDD) é a **referência técnica unificada e definitiva (*Single Source of Truth*)** para a **Plataforma de Lending as a Service (LaaS) e Engine de Resiliência para Crédito Consignado e PME**. 

O documento integra a especificação da PoC de alta vazão (capaz de absorver **10.000 TPS** na borda com **p95 < 100 ms** e proteger o *Core Banking* legado a **180 TPS**) com a **Arquitetura Alvo de Produção (Target Architecture)**. A solução resolve de forma definitiva o conflito entre a **fricção da originação de crédito com Inteligência Artificial, Open Finance e KYC/PLD** e a **necessidade de altíssima velocidade e resiliência no pico da "virada de folha" de margem consignável**.

### 1.2. Problema de Negócio & Hipóteses de Volumetria
O mercado de crédito consignado brasileiro atinge mais de **R$ 484 bilhões** em carteira ativa (servidores públicos, INSS e consignado privado CLT/eSocial). Na "virada de folha" mensal de margem consignável, o tráfego do banco sofre um pico brutal gerado por robôs e scripts automatizados de parceiros e correspondentes bancários ("pastinhas"), saltando de **50 TPS para 10.000 TPS** às 00:01.

O Core Banking legado atua como um gargalo físico com capacidade segura limitada a **180 TPS**. A arquitetura síncrona tradicional colapsa sob esse pico, resultando em exaustão de conexões, downtime operacional e perda de originação para a concorrência.

### 1.3. Requisitos Não-Funcionais Principais (NFRs)
* **Pico Extremo de Carga:** 10.000 TPS mantidos por 5 minutos (~3.000.000 de requisições).
* **SLA da Borda (Ingestão):** Resposta `HTTP 202 Accepted` em p95 < 100 ms e p99 < 200 ms.
* **Teto do Legado (Capacity Limit):** Vazão mantida rigorosamente em **<= 180 TPS**.
* **Smart Idempotency & FinOps:** 100% de prevenção contra duplicidade transacional usando Hash de Negócio SHA-256, eliminando custos desnecessários em nuvem e risco de pagamento/averbação dupla.
* **Fast-Fail Filtering:** Rejeição de propostas inelegíveis (INSS DDB 90 dias, estouro de margem MP 1.355/2026, sanções Febraban) na borda em < 5 ms sem tocar banco de dados ou filas.
* **Governança Multi-Tenant:** Rate limiting isolado por parceiro e conformidade com a Autorregulação Febraban/ABBC.

---

## 2. Fundamentação Regulatória e de Negócio

A arquitetura do sistema é desenhada para atender integralmente às exigências das normativas brasileiras de crédito e pagamentos:

* **Lei nº 10.820/2003 e MP nº 1.292/2025 (Programa Crédito do Trabalhador)**: Regulamenta o desconto em folha e a integração via eSocial e FGTS Digital.
* **Instruções Normativas INSS PRES/INSS nº 138/2022 e nº 172/2024**: Define a margem consignável de 45%, prazo máximo de 84 parcelas, bloqueio temporário de 90 dias (DDB) a novos benefícios para bancos não-originadores e obrigatoriedade da confirmação por biometria facial.
* **Medida Provisória nº 1.355/2026**: Exige a parametrização do motor de simulação e triagem para a transição gradual do teto de margem consignável de 45% para 40% a partir de 2027.
* **Circular Banco Central do Brasil (BCB) nº 3.978**: Estabelece os procedimentos e controles internos de Prevenção a Ilícitos (PLD) e validação de KYC.
* **Resolução CMN nº 4.292/2013 e Resolução Conjunta CMN/BCB nº 15/2025**: Regulamenta a portabilidade de crédito e o compartilhamento automatizado de operações via Open Finance.
* **Portarias MTE nº 433/2025 e nº 240/2026**: Define os papéis operacionais de Dataprev, SERPRO e Caixa Econômica Federal na governança do consignado privado e público, incluindo validação de vínculos no eSocial.
* **Autorregulação do Consignado (Febraban / ABBC)**: Exige monitoramento, contenção e aplicação de sanções administrativas contra o envio abusivo de requisições por robôs de correspondentes.

---

## 3. Visão Geral da Arquitetura Estagiada (Topologia Target IEEE 1016)

O sistema adota o **Padrão de Processamento Estagiado e Assíncrono em 3 Estágios**, garantindo o desacoplamento temporal absoluto entre a inteligência de originação, a borda de alta vazão e a liquidação no legado.

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ ESTÁGIO 1: PRÉ-ORIGINAÇÃO E FRONT-OFFICE INTELIGENTE (Off-Pico / Assíncrono)            │
│                                                                                         │
│  [ Cliente / PME / App Partner / LaaS ]                                                 │
│                │                                                                        │
│                ▼                                                                        │
│  ┌──────────────────────────┐     ┌──────────────────────────┐     ┌─────────────────┐  │
│  │ Smart Agent Service (AI) │ ──► │ Open Finance Analytics   │ ──► │ Engine KYC/PLD  │  │
│  │ (Análise Contextual)     │     │ (Leitura Extratos/Vendas)│     │ (Circ. BCB 3.978)│ │
│  └────────────┬─────────────┘     └──────────────────────────┘     └─────────────────┘  │
│               │                                                                         │
│               ▼                                                                         │
│  ┌───────────────────────────────────────────────────────────┐                          │
│  │ CCB Digital Generator (PDF Hash / Pre-Approved Token)     │                          │
│  │ Status: READY_FOR_SUBMISSION                              │                          │
│  └───────────────────────────────────────────────────────────┘                          │
└───────────────────────────────────────────┬─────────────────────────────────────────────┘
                                            │ (Disparo no Segundo 00:01)
                                            ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ ESTÁGIO 2: BORDA DE INGESTÃO COM GUARDRAILS (Virada de Folha - 00:01 às 10.000 TPS)      │
│                                                                                         │
│  [ Ingestion API Fleet (Java 21 / Virtual Threads / ECS Fargate) ]                      │
│                │                                                                        │
│                ▼                                                                        │
│  ┌───────────────────────────────────────────────────────────┐                          │
│  │ CAMADA DE TRIAGEM RÁPIDA DE REGRAS (Fast-Fail Filter)     │                          │
│  │ • In-Memory Cache (Caffeine / Redis Cluster)              │                          │
│  │ • Filtro DDB INSS (90 dias) / Teto Margem MP 1.355/2026   │                          │
│  │ • Rejeição em < 5 ms (HTTP 422 RFC 7807) sem Dynamo/SQS   │                          │
│  └────────────┬──────────────────────────────────────────────┘                          │
│               │ (Passou na Triagem)                                                     │
│               ▼                                                                         │
│  ┌───────────────────────────────────────────────────────────┐                          │
│  │ SMART IDEMPOTENCY ENGINE (Hash de Negócio SHA-256)        │                          │
│  │ • SHA256(CPF + Convênio + Valor + Parcelas + Data)        │                          │
│  └────────────┬──────────────────────────────────────────────┘                          │
│               │ (Escrita Condicional: attribute_not_exists(PK))                         │
│               ▼                                                                         │
│  ┌───────────────────────────────────────────────────────────┐                          │
│  │ Durable Inbox (Amazon DynamoDB - Single Table Design)     │                          │
│  │ Retém retentativas duplicadas atômica e gratuitamente     │                          │
│  └────────────┬──────────────────────────────────────────────┘                          │
│               │ (DynamoDB Streams)                                                      │
│               ▼                                                                         │
│  ┌───────────────────────────────────────────────────────────┐                          │
│  │ AWS Lambda Dispatcher (Smart QoS Routing)                 │                          │
│  │ • Fast Lane SQS (70% Budget = 126 TPS) -> CCB / OpenFin   │                          │
│  │ • Standard Lane SQS (30% Budget = 54 TPS)                 │                          │
│  └───────────────────────────────────────────────────────────┘                          │
└───────────────────────────────────────────┬─────────────────────────────────────────────┘
                                            │
                                            ▼ (Conta-Gotas Rigoroso a 180 TPS)
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ ESTÁGIO 3: PÓS-AVERBAÇÃO, FORMALIZAÇÃO FINAL & LIQUIDAÇÃO (Back-Office / Assíncrono)    │
│                                                                                         │
│  ┌───────────────────────────────────────────────────────────┐                          │
│  │ Worker Fleet (ECS Fargate + Resilience4j Rate Limiter)    │                          │
│  │ Consumo travado em estritamente 180 TPS                   │                          │
│  └────────────┬──────────────────────────────────────────────┘                          │
│               │                                                                         │
│               ▼                                                                         │
│  ┌───────────────────────────────────────────────────────────┐                          │
│  │ Core Banking Legado / Dataprev / eSocial / eConsig        │                          │
│  │ (Reserva e Averbação da Margem Consignável)               │                          │
│  └────────────┬──────────────────────────────────────────────┘                          │
│               │ (Sucesso na Averbação)                                                  │
│               ▼                                                                         │
│  ┌───────────────────────────────────────────────────────────┐                          │
│  │ Amazon Aurora PostgreSQL (Transactional Outbox Pattern)   │                          │
│  └────────────┬──────────────────────────────────────────────┘                          │
│               │                                                                         │
│               ▼                                                                         │
│  ┌───────────────────────────────────────────────────────────┐                          │
│  │ Settlement Engine (Assinatura Definitiva CCB + Pix/TED)   │                          │
│  └───────────────────────────────────────────────────────────┘                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Visões Arquiteturais Detalhadas (IEEE 1016 / ISO 42010)

### 4.1. Visão de Decomposição (Decomposition View)
* **`Smart-Agent-Service` (Python 3.12 / FastAPI ou Java 21)**: Atua no Estágio 1 qualificando o cliente PME/servidor, consumindo Open Finance e pré-formalizando a CCB.
* **`Ingestion-API-Service` (Java 21 / Spring Boot 3.3 / Virtual Threads)**: Atua no Estágio 2 executando a recepção HTTP, validação de token, **Fast-Fail Filter**, cálculo do Hash SHA-256 e gravação idempotente no DynamoDB em < 100 ms.
* **`Fast-Fail-Filter-Engine` (Componente de Borda)**: Motor embarcado no `Ingestion-API` com cache local (Caffeine) e réplicas de leitura Redis (AWS ElastiCache) que executa 5 Guardrails em < 5 ms.
* **`Dispatcher-Lambda-Service` (AWS Lambda)**: Consumidor reativo do DynamoDB Streams responsável pelo enfileiramento QoS no SQS.
* **`Worker-Execution-Service` (Java 21 / ECS Fargate)**: Consumidor das filas SQS que interage com o Core Banking e Dataprev/eSocial sob controle estrito de 180 TPS (Resilience4j).
* **`Settlement-Engine-Service` (Java 21)**: Consome os eventos do PostgreSQL Outbox para assinar a CCB e disparar Pix/TED.

---

### 4.2. Visão Algorítmica 1: Fast-Fail Filter Engine (5 Guardrails de Borda)

Para evitar que propostas inelegíveis consumam o orçamento de 180 TPS do legado, a `Ingestion-API-Service` executa 5 Guardrails em memória em **< 5 ms**:

```java
@Component
public class FastFailFilterEngine {

    private final LocalCacheService localCache; // Caffeine in-memory cache
    private final RedisClusterService redisCluster; // ElastiCache Redis read replicas

    public ValidationResult validateProposal(ProposalRequest request) {
        // Guardrail 1: Validação Estrutural e Criptográfica do Token
        if (!isTokenSignatureValid(request.getPreApprovedToken())) {
            return ValidationResult.fail("ERR_INVALID_TOKEN_SIGNATURE", "Token pré-aprovado inválido ou adulterado.", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Guardrail 2: Sanitização e Dígito Verificador de CPF
        if (!CpfValidator.isValid(request.getCustomerCpf())) {
            return ValidationResult.fail("ERR_INVALID_CPF", "CPF do tomador sintaticamente inválido.", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Guardrail 3: Trava de Bloqueio de Benefício INSS (DDB < 90 dias - IN 138/172)
        if ("INSS".equalsIgnoreCase(request.getAgreementType())) {
            boolean isDdbLocked = localCache.isCpfLockedInssDdb(request.getCustomerCpf());
            if (isDdbLocked && !request.isOriginatingBank()) {
                return ValidationResult.fail("ERR_INSS_DDB_LOCKED_90_DAYS", 
                    "Benefício INSS em período de bloqueio de 90 dias da DDB para operações de terceiros.", 
                    HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }

        // Guardrail 4: Validação de Margem e Prazos do Convênio (MP 1.355/2026 & Portaria MTE)
        if (request.getInstallments() > getMaxInstallmentsForAgreement(request.getAgreementType())) {
            return ValidationResult.fail("ERR_EXCEEDED_MAX_INSTALLMENTS", 
                "Prazo de parcelas superior ao limite regulatório do convênio.", 
                HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Guardrail 5: Sanção e Compliance de Parceiro (Autorregulação Febraban / ABBC)
        if (localCache.isTenantSuspended(request.getTenantId())) {
            return ValidationResult.fail("ERR_TENANT_SUSPENDED_FEBRABAN", 
                "Correspondente bancário temporariamente bloqueado por descumprimento da Autorregulação.", 
                HttpStatus.FORBIDDEN);
        }

        return ValidationResult.success();
    }
}
```

#### Eschema de Erro RFC 7807 (Fast-Fail Response)
Quando o filtro rejeita a proposta, responde em < 5 ms com `HTTP 422` ou `HTTP 403`:

```json
{
  "type": "urn:banco:errors:regulatory-ineligibility",
  "title": "Proposta Inelegível para Ingestão",
  "status": 422,
  "code": "ERR_INSS_DDB_LOCKED_90_DAYS",
  "detail": "O benefício do CPF informado possui menos de 90 dias de concessão (DDB) e está bloqueado para operações por bancos não-originadores conforme IN INSS 138/172.",
  "timestamp": "2026-10-14T07:05:00Z",
  "invalidParams": [
    { "name": "customerCpf", "reason": "Benefício INSS sob trava temporária DDB 90 dias" }
  ]
}
```

---

### 4.3. Visão Algorítmica 2: Smart Idempotency por Hash de Negócio (SHA-256)

Para neutralizar a criação de novos UUIDs por robôs de correspondentes, o sistema calcula uma impressão digital determinística:

1. **String Canônica**:
   `CanonicalString = CPF | AgreementType | Amount | Installments | DateUTC`
2. **Cálculo SHA-256**:
   ```java
   String rawCanonicalString = String.format("%s|%s|%s|%s|%s",
       cleanedCpf, agreementType.trim().toUpperCase(), formattedAmount, installments, currentUtcDateStr);
   String businessHash = DigestUtils.sha256Hex(rawCanonicalString);
   ```
3. **Escrita Condicional Atômica no DynamoDB (`DurableInbox`)**:
   * **PK**: `HASH#<businessHash>` | **SK**: `METADATA`
   * **Condition**: `attribute_not_exists(PK)`
4. **Comportamento sob Duplicata (`ConditionalCheckFailedException`)**:
   Em < 15 ms, a API captura a exceção e responde:
   * Status: `HTTP 202 Accepted`
   * Header: `X-Idempotency-Status: DUPLICATE_REJECTED` / `X-Idempotency-Replay: true`
   * Payload: `{ "proposalId": "HASH#<businessHash>", "status": "ALREADY_RECEIVED", "message": "Proposta idêntica já recebida e em processamento no backlog." }`

---

### 4.4. Visão Algorítmica 3: Governança de QoS (70/30 Fast/Standard SQS Routing)

O `AWS Lambda Dispatcher` consome o DynamoDB Streams e classifica o tráfego nas filas SQS:

* **`sqs-fast-lane-queue` (70% do orçamento do legado = 126 TPS)**: Propostas com CCB pré-formalizada, portabilidades Open Finance (Resolução CMN nº 4.292/2013) e operações de parceiros VIP.
* **`sqs-standard-lane-queue` (30% do orçamento do legado = 54 TPS)**: Propostas convencionais de canais padrão.

---

## 5. Esquemas de Dados, Persistência e Contrato OpenAPI

### 5.1. Contrato OpenAPI 3.0.3 Completo (`POST /v1/proposals`)

```yaml
openapi: 3.0.3
info:
  title: Ingestion API - Crédito Consignado & LaaS
  version: 4.0.0
paths:
  /v1/proposals:
    post:
      summary: Ingestão síncrona durável de proposta de crédito consignado
      headers:
        Authorization:
          schema:
            type: string
          required: true
          description: Token Bearer JWT do parceiro ou cliente
        Idempotency-Key:
          schema:
            type: string
            format: uuid
          required: false
          description: Chave de idempotência enviada pelo cliente (opcional, substituída pelo Hash de Negócio)
        X-Tenant-ID:
          schema:
            type: string
          required: true
          description: Identificador do correspondente bancário / parceiro LaaS
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ProposalRequest'
      responses:
        '202':
          description: Proposta aceita e registrada com sucesso para processamento
          headers:
            X-Trace-ID:
              schema:
                type: string
            X-Proposal-Hash:
              schema:
                type: string
            X-Idempotency-Status:
              schema:
                type: string
                enum: [ACCEPTED_NEW, DUPLICATE_REJECTED]
          content:
            application/json:
              schema:
                type: object
                properties:
                  proposalId:
                    type: string
                    example: "HASH#a3f8c9d2e1b4..."
                  status:
                    type: string
                    example: "RECEIVED"
                  estimatedDrainTime:
                    type: string
                    example: "00:02:15"
                  receivedAt:
                    type: integer
                    example: 1772841600
        '422':
          description: Proposta inelegível barrada no Fast-Fail Filter (RFC 7807)
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetails'
        '429':
          description: Rate limit do parceiro excedido na borda (Febraban/ABBC)

components:
  schemas:
    ProposalRequest:
      type: object
      required:
        - customerCpf
        - agreementType
        - amount
        - installments
        - preApprovedToken
      properties:
        customerCpf:
          type: string
          example: "12345678901"
        agreementType:
          type: string
          enum: [INSS, ESOCIAL_CLT, SIAPE_PUBLICO, PRODESP]
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
        preApprovedToken:
          type: string
          example: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    ProblemDetails:
      type: object
      properties:
        type:
          type: string
        title:
          type: string
        status:
          type: integer
        code:
          type: string
        detail:
          type: string
        timestamp:
          type: string
```

---

### 5.2. Esquema da Tabela `DurableInbox` (Amazon DynamoDB)

| Atributo | Tipo | Função / Descrição |
| :--- | :--- | :--- |
| **`PK`** | String | Partition Key atômica (`HASH#<SHA256_Negócio>`) |
| **`SK`** | String | Sort Key (`METADATA`) |
| **`tenantId`** | String | Código do parceiro / correspondente bancário |
| **`agreementType`** | String | Categoria do convênio (`INSS`, `ESOCIAL_CLT`, `SIAPE`) |
| **`isPriority`** | Boolean | Flag de prioridade (Portabilidade Open Finance / CCB / VIP) |
| **`payload`** | String (JSON) | JSON serializado e comprimido da proposta |
| **`status`** | String | Status (`RECEIVED`, `DISPATCHED`, `FAILED`) |
| **`createdAt`** | Number | Timestamp Epoch da ingestão |
| **`ttl`** | Number | Epoch de expiração automática (24 horas) |

---

### 5.3. Script DDL do Estado Relacional (Amazon Aurora PostgreSQL)

```sql
-- Tabela do Estado Consolidado da Proposta
CREATE TABLE proposal (
    id UUID PRIMARY KEY,
    business_hash VARCHAR(64) UNIQUE NOT NULL,
    customer_cpf VARCHAR(11) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    agreement_type VARCHAR(20) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    installments INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_proposal_customer ON proposal(customer_cpf);
CREATE INDEX idx_proposal_tenant_status ON proposal(tenant_id, status);

-- Tabela para o Padrão Transactional Outbox
CREATE TABLE proposal_event (
    event_id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES proposal(id),
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    processed BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_outbox_unprocessed ON proposal_event(created_at) WHERE processed = FALSE;
```

---

## 6. Repositório de Decisões de Arquitetura (ADRs / MADR)

### ADR-001: Adoção de Java 21 com Virtual Threads para a Ingestion API
* **Status**: Aprovado.
* **Decisão**: Utilizar Java 21 com Spring Boot 3.3 e Virtual Threads (`spring.threads.virtual.enabled=true`).
* **Justificativa**: Manipula dezenas de milhares de conexões HTTP simultâneas em instâncias Fargate leves com I/O não-bloqueante simples, sem a complexidade reativa do Spring WebFlux.

### ADR-002: Smart Idempotency por Hash de Negócio no DynamoDB
* **Status**: Aprovado.
* **Decisão**: Utilizar a gravação condicional no DynamoDB (`attribute_not_exists`) tendo como PK o Hash SHA-256 dos dados imutáveis do contrato (`CPF + Convênio + Valor + Parcelas + Data`).
* **Justificativa**: Neutraliza scripts de robôs que regeneram UUIDs no cabeçalho, descartando duplicatas atômica e gratuitamente na borda do banco NoSQL.

### ADR-003: Processamento Estagiado em 3 Fases (Desacoplamento Front/Back)
* **Status**: Aprovado.
* **Decisão**: Isolar a inteligência de originação (IA, Open Finance, KYC) no **Estágio 1 (Off-Pico)**, liberando o **Estágio 2 (00:01)** para processar apenas tokens pré-aprovados de alta velocidade.
* **Justificativa**: Garante a resposta da borda em < 100 ms no pico sem abrir mão do compliance e da inteligência de produto.

### ADR-004: Camada de Triagem Rápida em Memória (Fast-Fail Filter)
* **Status**: Aprovado.
* **Decisão**: Embarcar no `Ingestion-API` um motor em memória (Caffeine/Redis Cluster) que valida regras regulatórias (DDB 90 dias, limites de margem MP 1.355, sanções Febraban) em < 5 ms.
* **Justificativa**: Evita que propostas sabidamente inelegíveis consumam escritas no DynamoDB e ocupem o orçamento limitado de 180 TPS do legado.

### ADR-005: Transactional Outbox Pattern no Aurora PostgreSQL
* **Status**: Aprovado.
* **Decisão**: Gravar eventos na tabela `proposal_event` dentro da mesma transação do estado da proposta no PostgreSQL para consumo assíncrono pelo `Settlement-Engine`.
* **Justificativa**: Garante consistência transacional relacional para liquidação do Pix/TED sem dependência de escrita dupla atômica no banco de dados.

---

## 7. Telemetria, Observabilidade e FinOps (SRE Google Standards)

### 7.1. Métricas Principais expostas no Prometheus/Micrometer
* **`ingestion_accepted_total`**: Total de propostas aceitas na borda (`HTTP 202`).
* **`ingestion_fast_fail_rejected_total`**: Propostas inelegíveis barradas no Fast-Fail Filter em < 5 ms (`HTTP 422`).
* **`ingestion_duplicate_rejected_total`**: Retentativas duplicadas bloqueadas pelo Smart Idempotency por Hash.
* **`net_backlog_growth`**: Taxa de Chegada (\(\lambda\)) - Taxa de Processamento (\(\mu\)).
* **`effective_drain_time_seconds`**: Tempo preditivo estimado para liquidação completa da fila (`QueueDepth / 180 TPS`).
* **`legacy_throughput_tps`**: Vazão entregue ao Core Banking (alerta automático se `> 180 TPS`).

---

## 8. Matriz de Rastreabilidade de Requisitos (SRS -> Target Architecture -> DoD k6)

| Requisito de Negócio / Regulação | Componente de Arquitetura Alvo | Estratégia de Validação e Aceite (DoD) |
| :--- | :--- | :--- |
| **Absorção de Pico (10.000 TPS)** | `Ingestion-API` (Java 21 Virtual Threads) + DynamoDB | Teste no k6 injetando 3.000.000 req em 5 min com p95 < 100 ms. |
| **Triagem Rápida & Inelegibilidade** | `Fast-Fail-Filter-Engine` (In-Memory Caffeine/Redis) | Disparo de CPFs sob trava DDB resulta em HTTP 422 em < 5 ms. |
| **Proteção do Core Banking (180 TPS)** | `Worker-Fleet` + Resilience4j Rate Limiter | Grafana comprova que a taxa no Mock Legado nunca excede 180 TPS. |
| **Eliminação de Duplicatas & FinOps** | DynamoDB `attribute_not_exists` + Business Hash | Disparo de 50.000 req idênticas resulta em exatamente 1 registro no PostgreSQL. |
| **Prioridade de Portabilidade (Open Finance)** | AWS Lambda Dispatcher + SQS Fast Lane (70/30) | Injeção mista comprova processamento acelerado da Fast Lane no SQS. |

---

📄 **Documento publicado no repositório de arquitetura como a versão final e definitiva.**  
*Autorização de Engenharia: Staff / Principal Systems Engineer.*
