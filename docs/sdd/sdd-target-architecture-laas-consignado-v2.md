# Software Design Document (SDD) - Arquitetura Alvo de Produção

> Nota de escopo — revisão de 16/10/2026: Este documento registra uma especificação ou decisão de arquitetura. Metas, componentes target e declarações de aprovação não comprovam implementação ou homologação da PoC atual. Consulte o [índice documental](../README.md) e as limitações atuais antes de usar este texto como descrição do ambiente.


**Projeto:** Plataforma LaaS, Agente IA & Engine de Alta Vazão para Crédito Consignado e PME  
**Maturidade Arquitetural:** Staff / Principal Systems Engineer  
**Status:** Approved for Engineering & Board Architecture Review  
**Padrão Documental:** IEEE 1016-2009 & ISO/IEC/IEEE 42010  
**Versão:** 2.1 (Incorporação da Resiliência por Smart Idempotency & Algoritmo de Hash de Negócio)  

---

## 1. Controle do Documento & Resumo Executivo

### 1.1. Objetivo
Este documento especifica a **Arquitetura Alvo de Produção (Target Architecture)** para a plataforma de **Lending as a Service (LaaS)** e **Crédito Consignado / PME**. 

A solução evolui a PoC de resiliência e absorção de alta vazão (suportando picos de **10.000 TPS** e protegendo o *Core Banking* a **180 TPS**) para um produto de originação de crédito pronto para produção. O documento resolve as brechas de engenharia identificadas em análises de estresse (Red Team), em especial a **vulnerabilidade de idempotência em sistemas de alta frequência** durante a "virada de folha" de margem consignável.

### 1.2. O Padrão Arquitetural: Desacoplamento Estagiado em 3 Fases
Para evitar que a latência e o custo de chamadas a APIs externas (Open Finance, consultas de risco, biometria e geração de CCB) destruam a capacidade de ingestão no momento crítico da abertura de margem, o sistema adota um **Padrão de Processamento Estagiado e Assíncrono em 3 Estágios**:

1. **Estágio 1 — Pré-Originação e Front-Office Inteligente (Off-Pico / Durante o Dia)**: Qualificação de clientes/PME via Agente IA, leitura de extratos/recebíveis via Open Finance, checagens de KYC e conformidade PLD (Circular BCB nº 3.978) e pré-formalização da Cédula de Crédito Bancário (CCB) Digital.
2. **Estágio 2 — Borda de Ingestão & Alta Vazão (Core de Ingestão — 00:01)**: Ingestão atômica em Java 21 / Virtual Threads no segundo 00:01, recebendo apenas o payload leve pré-aprovado, garantindo **HTTP 202 Accepted em < 100 ms** a 10.000 TPS com **Smart Idempotency por Hash de Negócio** no Amazon DynamoDB e roteamento por QoS (Fast/Standard Lane em SQS).
3. **Estágio 3 — Pós-Averbação, Formalização Final & Liquidação (Back-Office / Assíncrono)**: Consumo cadenciado da fila a 180 TPS via Workers ECS Fargate e Resilience4j Rate Limiter, averbação no órgão pagador (Dataprev/eSocial), confirmação relacional via Transactional Outbox (Aurora PostgreSQL) e liquidação do Pix/TED com assinatura final da CCB.

---

## 2. Fundamentação Regulatória e de Negócio

A arquitetura do sistema é desenhada para atender integralmente às exigências das normativas brasileiras de crédito e pagamentos:

* **Lei nº 10.820/2003 e MP nº 1.292/2025 (Programa Crédito do Trabalhador)**: Regulamenta o desconto em folha e a integração via eSocial e FGTS Digital.
* **Instruções Normativas INSS PRES/INSS nº 138/2022 e nº 172/2024**: Define a margem consignável de 45%, prazo máximo de 84 parcelas, bloqueio temporário de 90 dias (DDB) a novos benefícios e obrigatoriedade da confirmação por biometria facial.
* **Medida Provisória nº 1.355/2026**: Exige a parametrização do motor de simulação para a transição gradual do teto de margem consignável de 45% para 40% a partir de 2027.
* **Circular Banco Central do Brasil (BCB) nº 3.978**: Estabelece os procedimentos e controles internos de Prevenção a Ilícitos (PLD) e validação de KYC.
* **Resolução CMN nº 4.292/2013 e Resolução Conjunta CMN/BCB nº 15/2025**: Regulamenta a portabilidade de crédito e o compartilhamento automatizado de operações via Open Finance.
* **Portarias MTE nº 433/2025 e nº 240/2026**: Define os papéis operacionais de Dataprev, SERPRO e Caixa Econômica Federal na governança do consignado privado e público.
* **Autorregulação do Consignado (Febraban / ABBC)**: Exige monitoramento, contenção e aplicação de sanções administrativas contra o envio abusivo de requisições por robôs e scripts automatizados de correspondentes bancários.

---

## 3. Visões Arquiteturais (Padrão IEEE 1016-2009 / ISO 42010)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ ESTÁGIO 1: PRÉ-ORIGINAÇÃO E FRONT-OFFICE INTELIGENTE (Off-Pico / Assíncrono)            │
│                                                                                         │
│  [ Cliente / PME / App Partner ]                                                         │
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
│ ESTÁGIO 2: BORDA DE INGESTÃO DE ALTA VAZÃO (Virada de Folha - 00:01 às 10.000 TPS)       │
│                                                                                         │
│  [ Ingestion API Fleet (Java 21 / Virtual Threads / ECS Fargate) ]                      │
│  • Responde HTTP 202 Accepted em < 100 ms                                               │
│  • Cálculo do Hash Criptográfico do Negócio: SHA256(CPF+Convênio+Valor+Parcelas+Data)   │
│  • Validação WAF v2 + Rate Limiting Multi-Tenant por Parceiro                           │
│                │                                                                        │
│                ▼ (Escrita Condicional Atômica: attribute_not_exists(PK))                │
│  ┌───────────────────────────────────────────────────────────┐                          │
│  │ Durable Inbox (Amazon DynamoDB - Single Table Design)     │                          │
│  │ PK = HASH#<SHA256_Negócio>                                │                          │
│  │ Bloqueia e retém retentativas com novos UUIDs do robô     │                          │
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

### 3.1. Visão de Decomposição (Decomposition View)

O sistema é decomposto em microsserviços autônomos, organizados segundo o ciclo de vida da transação:

* **`Ingestion-API-Service` (Java 21 / Spring Boot 3.3 / Virtual Threads)**: Responsável exclusivo pela validação do schema HTTP, cálculo do hash de negócio, verificação de idempotência no DynamoDB e resposta em < 100 ms.
* **`Dispatcher-Lambda-Service` (AWS Lambda em Java/Node)**: Consumidor reativo do DynamoDB Streams que executa a classificação de QoS e enfileira no Amazon SQS.
* **`Worker-Execution-Service` (Java 21 / ECS Fargate)**: Consumidor das filas SQS que gerencia o fluxo de averbação junto ao Core Banking e Dataprev/eSocial sob controle de vazão (180 TPS).
* **`Settlement-Engine-Service` (Java 21)**: Consome a tabela de eventos do PostgreSQL (Outbox) para efetuar a assinatura da CCB e transferência de recursos via Pix/TED.

---

### 3.2. Visão de Detalhamento Algorítmico: Smart Idempotency por Hash de Negócio (Detail View)

#### A Falha do Modelo Convencional (Header-Based Idempotency)
No modelo tradicional de mercado (ex: Stripe/IETF), a idempotência depende de uma chave enviada pelo cliente no cabeçalho HTTP (`Idempotency-Key` com UUID v4). Em cenários de estresse de correspondentes bancários ("pastinhas"), os scripts automatizados costumam regenerar um **novo UUID v4 a cada tentativa de retry** ao sofrerem timeouts ou lentidão de rede. Isso faz com que a API de ingestão trate as retentativas como **novas propostas**, inserindo duplicatas no banco de dados e inundando as filas SQS (Falha FinOps e Risco de Duplicidade).

#### O Algoritmo de Smart Idempotency (Business Domain Fingerprint)
Para neutralizar esse comportamento dos robôs na borda, a `Ingestion-API-Service` substitui a dependência do header cliente por uma **impressão digital determinística do domínio de negócio (Business Hash)**:

1. **Normalização e Extração dos Atributos de Domínio**:
   Quando a requisição chega ao endpoint `POST /v1/proposals`, o serviço extrai e higieniza cinco campos imutáveis do contrato de crédito:
   * `cpf`: Número do CPF do tomador (apenas os 11 dígitos numéricos).
   * `agreementType`: Código do convênio (`INSS`, `ESOCIAL`, `SOUGOV`, `PRODESP`).
   * `amount`: Valor solicitado formatado em ponto fixo (`"15000.00"`).
   * `installments`: Quantidade de parcelas (`"84"`).
   * `dateWindow`: Data do dia da operação no formato UTC (`YYYY-MM-DD`).

2. **Cálculo da Assinatura Criptográfica SHA-256**:
   ```java
   String rawCanonicalString = String.format("%s|%s|%s|%s|%s",
       cleanedCpf,
       agreementType.trim().toUpperCase(),
       formattedAmount,
       installments,
       currentUtcDateStr
   );
   
   String businessHash = DigestUtils.sha256Hex(rawCanonicalString);
   ```

3. **Gravação Condicional Atômica no DynamoDB (Durable Inbox)**:
   A `Ingestion-API` constrói a chave primária da tabela `DurableInbox` utilizando o prefixo de domínio `HASH#`:
   * **Partition Key (PK)**: `HASH#<businessHash>`
   * **Sort Key (SK)**: `METADATA`
   * **ConditionExpression**: `attribute_not_exists(PK)`

   ```java
   PutItemRequest putItemRequest = PutItemRequest.builder()
       .tableName("DurableInbox")
       .item(Map.of(
           "PK", AttributeValue.fromS("HASH#" + businessHash),
           "SK", AttributeValue.fromS("METADATA"),
           "clientProvidedUuid", AttributeValue.fromS(headerClientUuid),
           "tenantId", AttributeValue.fromS(tenantId),
           "payload", AttributeValue.fromS(compressedPayload),
           "status", AttributeValue.fromS("RECEIVED"),
           "createdAt", AttributeValue.fromN(String.valueOf(Instant.now().toEpochMilli())),
           "ttl", AttributeValue.fromN(String.valueOf(Instant.now().plus(24, ChronoUnit.HOURS).getEpochSecond()))
       ))
       .conditionExpression("attribute_not_exists(PK)")
       .build();
   ```

4. **Tratamento de Concorrência e Resposta HTTP**:
   * **Sucesso na Gravação (Chave Inédita)**: A proposta é registrada na `DurableInbox`. A API retorna `HTTP 202 Accepted` com os cabeçalhos:
     * `X-Proposal-Hash`: `<businessHash>`
     * `X-Idempotency-Status`: `ACCEPTED_NEW`
     * Payload JSON: `{ "proposalId": "HASH#<businessHash>", "status": "RECEIVED", "estimatedDrainTime": "00:03:45" }`
   * **Falha de Condição (`ConditionalCheckFailedException`)**: O DynamoDB rejeita a inserção atomicamente em nível de partição física. O handler em Java captura a exceção e responde imediatamente em < 15 ms:
     * Status: `HTTP 202 Accepted`
     * `X-Idempotency-Status`: `DUPLICATE_REJECTED`
     * `X-Idempotency-Replay`: `true`
     * Payload JSON: `{ "proposalId": "HASH#<businessHash>", "status": "ALREADY_RECEIVED", "message": "Proposta idêntica já recebida e em processamento no backlog." }`
   * **Resultado**: **Zero eventos extras gerados no SQS**, zero tráfego repassado aos workers, zero custo de computação downstream e eliminação absoluta de duplicações resultantes de scripts de robôs.

---

## 4. Registro Formal de Decisões de Arquitetura (ADRs / MADR)

### ADR-001: Adoção de Java 21 com Virtual Threads para a Ingestion API
* **Status**: Aprovado.
* **Contexto**: A Ingestion API precisa suportar 10.000 TPS de concorrência com latência p95 < 100 ms sob alto volume de I/O em rede com o DynamoDB.
* **Decisão**: Utilizar Java 21 com Spring Boot 3.3 e Virtual Threads habilitadas (`spring.threads.virtual.enabled=true`).
* **Justificativa**: Virtual Threads eliminam o custo de alternância de contexto de threads do sistema operacional, permitindo lidar com dezenas de milhares de conexões HTTP simultâneas em instâncias Fargate leves sem a complexidade reativa de código em Spring WebFlux/RxJava.

### ADR-002: Smart Idempotency por Hash de Negócio no DynamoDB
* **Status**: Aprovado.
* **Contexto**: Robôs de correspondentes retransmitem a mesma proposta com novos UUIDs de cabeçalho no pico de tráfego, contornando a idempotência tradicional.
* **Decisão**: Utilizar a gravação condicional no DynamoDB (`attribute_not_exists`) tendo como chave primária o Hash SHA-256 dos dados essenciais do contrato (`CPF + Convênio + Valor + Parcelas + Data`).
* **Justificativa**: Garante que retentativas anômalas sejam descartadas atômica e gratuitamente na borda do banco NoSQL, reduzindo os custos de FinOps em nuvem e garantindo o risco zero de averbações duplicadas.

### ADR-003: Processamento Estagiado em 3 Fases (Desacoplamento de Front/Back)
* **Status**: Aprovado.
* **Contexto**: Operações de IA, Open Finance, KYC/PLD e biometria levam de 3 a 15 segundos, o que inviabiliza a resposta em < 100 ms no pico das 00:01.
* **Decisão**: Isolar a inteligência de originação no **Estágio 1 (Off-Pico)**, permitindo que o segundo 00:01 do **Estágio 2** receba apenas o token pré-aprovado de alta velocidade.
* **Justificativa**: Protege a disponibilidade do banco no pico sem abrir mão dos recursos de inteligência e compliance do produto.

---

## 5. Design de Dados e Schemas de Persistência

### 5.1. Durable Inbox (Amazon DynamoDB - Single Table Design)
* **Table Name**: `DurableInbox`
* **Partition Key (PK)**: `String` (ex: `HASH#a3f8c9...` ou `TENANT#PARATI#HASH#a3f8c9...`)
* **Sort Key (SK)**: `String` (ex: `METADATA`)
* **Global Secondary Index (GSI1)**: `GSI1PK` (`CLIENT_KEY#<UUID_Client>`), `GSI1SK` (`METADATA`) para consultas pontuais por chave fornecida pelo parceiro.
* **TTL Attribute**: `ttl` (Unix Timestamp para expurgo automático do registro após 24 horas).

### 5.2. Estado Relacional (Amazon Aurora PostgreSQL)
Entidades do banco relacional de produção no Estágio 3:

```sql
-- Tabela de Estado Final da Proposta
CREATE TABLE proposal (
    id UUID PRIMARY KEY,
    business_hash VARCHAR(64) UNIQUE NOT NULL,
    customer_cpf VARCHAR(11) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    agreement_type VARCHAR(20) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    installments INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_proposal_customer ON proposal(customer_cpf);
CREATE INDEX idx_proposal_tenant_status ON proposal(tenant_id, status);

-- Tabela para o Padrão Transactional Outbox
CREATE TABLE proposal_event (
    event_id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES proposal(id),
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_outbox_unprocessed ON proposal_event(created_at) WHERE processed = FALSE;
```

---

## 6. Telemetria, Observabilidade e FinOps (SRE Google Standards)

Para garantir o monitoramento completo do sistema sob carga de 10.000 TPS, os serviços expõem métricas customizadas para o Prometheus:

* **`ingestion_accepted_total`**: Total de propostas aceitas na borda com código `HTTP 202`.
* **`ingestion_duplicate_rejected_total`**: Contagem de requisições duplicadas filtradas pelo algoritmo de **Smart Idempotency por Hash de Negócio**.
* **`legacy_throughput_tps`**: Medidor de vazão repassada ao Core Banking (alerta se `> 180 TPS`).
* **`effective_drain_time_seconds`**: Métrica preditiva calculada via `QueueDepth / EffectiveProcessingRate`, indicando no Grafana o tempo necessário para esvaziar o backlog acumulado na virada de folha.

---

## 7. Matriz de Rastreabilidade de Requisitos (SRS -> Target Architecture -> DoD k6)

| Requisito de Negócio / Regulação | Componente de Arquitetura Alvo | Estratégia de Validação e Aceite (DoD) |
| :--- | :--- | :--- |
| **Absorção de Pico (10.000 TPS)** | `Ingestion-API` (Java 21 Virtual Threads) + DynamoDB | Teste de carga no k6 injetando 3.000.000 req em 5 min com p95 < 100 ms. |
| **Proteção do Core Banking (180 TPS)** | `Worker-Fleet` + Resilience4j Rate Limiter | Grafana comprova que a taxa no Mock Legado nunca excede 180 TPS. |
| **Eliminação de Duplicatas & FinOps** | DynamoDB `attribute_not_exists` + Business Hash | Disparo de 50.000 req idênticas resulta em exatamente 1 registro no PostgreSQL. |
| **Prioridade de Portabilidade (Open Finance)** | AWS Lambda Dispatcher + SQS Fast Lane (70/30) | Injeção mista comprova processamento acelerado da Fast Lane no SQS. |
| **Compliance INSS (Biometria / 90 dias)** | Estágio 1 `KYC-Compliance-Service` | Propostas de CPFs em período de bloqueio DDB são rejeitadas antes da fila. |

---

📄 **Documento publicado no repositório de arquitetura.**  
*Autorização de Engenharia: Staff / Principal Systems Engineer.*
