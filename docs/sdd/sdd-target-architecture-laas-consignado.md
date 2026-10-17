# Software Design Document (SDD) - Arquitetura Alvo de Produção

> Nota de escopo — revisão de 16/10/2026: Este documento registra uma especificação ou decisão de arquitetura. Metas, componentes target e declarações de aprovação não comprovam implementação ou homologação da PoC atual. Consulte o [índice documental](../README.md) e as limitações atuais antes de usar este texto como descrição do ambiente.


**Projeto:** Plataforma LaaS, Agente IA & Engine de Alta Vazão para Crédito Consignado e PME  
**Maturidade Arquitetural:** Staff / Principal Systems Engineer  
**Status:** Ready for Architecture Board & Implementation Roadmap  
**Versão:** 2.0 (Evolução da PoC de Resiliência para Plataforma Target de Produção)  

---

## 1. Controle do Documento & Resumo Executivo

### 1.1. Objetivo
Este documento especifica a **Arquitetura Alvo de Produção (Target Architecture)** para a plataforma de **Lending as a Service (LaaS)** e **Crédito Consignado / PME**. 

A solução evolui a PoC de resiliência e absorção de alta vazão (capaz de suportar picos de **10.000 TPS** e proteger o *Core Banking* a **180 TPS**) para um produto completo de originação inteligente. A arquitetura resolve o conflito entre a **fricção da originação com Inteligência Artificial, Open Finance e KYC/PLD** e a **necessidade de altíssima velocidade e resiliência no pico da "virada de folha" de margem consignável**.

### 1.2. O Padrão Arquitetural: Desacoplamento Estagiado em 3 Fases
Para evitar que a latência e o custo de chamadas a APIs externas (Open Finance, consultas de risco, biometria e geração de CCB) destruam a capacidade de ingestão no momento crítico da abertura de margem, o sistema adota um **Padrão de Processamento Estagiado e Assíncrono em 3 Estágios**:

1. **Estágio 1 — Pré-Originação e Front-Office Inteligente (Off-Pico / Durante o Dia)**: Qualificação de clientes/PME via Agente IA, leitura de extratos/recebíveis via Open Finance, checagens de KYC e conformidade PLD (Circular BCB nº 3.978) e pré-formalização da Cédula de Crédito Bancário (CCB) Digital.
2. **Estágio 2 — Borda de Ingestão & Alta Vazão (O Core da PoC — 00:01)**: Ingestão atômica em Java 21 / Virtual Threads no segundo 00:01, recebendo apenas o payload leve pré-aprovado, garantindo **HTTP 202 Accepted em < 100 ms** a 10.000 TPS com deduplicação atômica no Amazon DynamoDB e roteamento por QoS (Fast/Standard Lane em SQS).
3. **Estágio 3 — Pós-Averbação, Formalização Final & Liquidação (Back-Office / Assíncrono)**: Consumo cadenciado da fila a 180 TPS via Workers ECS Fargate e Resilience4j Rate Limiter, averbação no órgão pagador (Dataprev/eSocial), confirmação relacional via Transactional Outbox (Aurora PostgreSQL) e liquidação do Pix/TED com assinatura final da CCB.


## 2. Fundamentação Regulatória e de Negócio

A arquitetura do sistema é desenhada para atender integralmente às exigências das normativas brasileiras de crédito e pagamentos:

* **Lei nº 10.820/2003 e MP nº 1.292/2025 (Programa Crédito do Trabalhador)**: Regulamenta o desconto em folha e a integração via eSocial e FGTS Digital.
* **Instruções Normativas INSS PRES/INSS nº 138/2022 e nº 172/2024**: Define a margem consignável de 45%, prazo máximo de 84 parcelas, bloqueio temporário de 90 dias a novos benefícios e a obrigatoriedade da confirmação por biometria facial.
* **Circular Banco Central do Brasil (BCB) nº 3.978**: Estabelece os procedimentos e controles internos de Prevenção a Ilícitos (PLD) e validação de KYC.
* **Resolução CMN nº 4.292/2013 e Resolução Conjunta CMN/BCB nº 15/2025**: Regulamenta a portabilidade de crédito e o compartilhamento de dados/operações via Open Finance.
* **Portarias MTE nº 433/2025 e nº 240/2026**: Define a atuação conjunta de Dataprev, SERPRO e Caixa Econômica Federal.
* **Autorregulação do Consignado (Febraban / ABBC)**: Exige controle e sanção contra o uso abusivo de rotinas automatizadas e assédio comercial por correspondentes e parceiros.

---

## 3. Visão Geral da Arquitetura Estagiada (Topologia Target)

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
│  • Validação WAF v2 + Rate Limiting Multi-Tenant por Parceiro                           │
│                │                                                                        │
│                ▼ (Escrita Condicional: attribute_not_exists)                            │
│  ┌───────────────────────────────────────────────────────────┐                          │
│  │ Durable Inbox (Amazon DynamoDB - Single Table Design)     │                          │
│  │ Descarta 499 envios duplicados atômica e gratuitamente     │                          │
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


## 4. Especificação Técnica dos Módulos

### 4.1. Estágio 1: Módulo de Originação Inteligente (Front-Office)

Este módulo roda **fora da janela crítica de pico** (durante o dia) para qualificar o cliente e preparar a operação.

* **`Smart-Agent-Service` (Python 3.12 / FastAPI ou Java 21)**:
  * Orquestra a jornada do usuário/PME através de conversação natural ou integrações de API B2B2C (LaaS).
  * Consome o consentimento de **Open Finance** para buscar histórico de extratos bancários, faturamento e receita recorrente.
* **`KYC-Compliance-Service`**:
  * Executa a validação de listas de pessoas expostas politicamente e checagem de regras de conformidade conforme a **Circular BCB nº 3.978**.
  * Valida a prova de vida por **biometria facial** e status do benefício (verificando a trava de 90 dias do INSS).
* **`CCB-Generator-Service`**:
  * Emite a minuta da **Cédula de Crédito Bancário (CCB) Digital** com os parâmetros financeiros (valor, taxa CET, parcelas e IOF).
  * Gera o `preApprovedToken` assinado criptograficamente contendo a hash do contrato e o `idempotencyKey`.
  * Grava o estado pré-aprovado no banco com status `READY_FOR_SUBMISSION`.

### 4.2. Estágio 2: Borda de Ingestão e Alta Vazão (Core da PoC)

No segundo 00:01, o sistema recebe a carga massiva de **10.000 TPS** contendo apenas os tokens levemente montados no Estágio 1.

* **AWS WAF v2 & ALB Multi-Tenant**:
  * Aplica regras de *rate limiting* por IP e por `X-Tenant-ID` (parceiro/correspondente).
  * Bloqueia comportamentos em descumprimento com a Autorregulação Febraban/ABBC, respondendo `HTTP 429`.
* **`Ingestion-API-Service` (Java 21 / Virtual Threads / Spring Boot 3.3)**:
  * Endpoint de altíssima concorrência: `POST /v1/proposals`.
  * Executa apenas validação de schema e verificação do `preApprovedToken`. Zero chamadas síncronas a banco de dados relacional ou APIs externas.
* **`DurableInbox` (Amazon DynamoDB - Single Table Design)**:
  * Gravação atômica da intenção de crédito.
  * **Idempotência FinOps**: Gravação condicional com a expressão `attribute_not_exists(idempotencyKey)`. Se um script enviar a mesma proposta 500 vezes, a 1ª é gravada e as 499 subsequentes são descartadas atômica e gratuitamente na borda, retornando `HTTP 202 Accepted` com o mesmo protocolo.
* **`AWS Lambda Dispatcher` & Governança de QoS (Amazon SQS)**:
  * Função reativa ativada via DynamoDB Streams.
  * Analisa os metadados da proposta e distribui o tráfego em duas filas no SQS:
    * **`sqs-fast-lane-queue` (70% da capacidade do legado / 126 TPS)**: Propostas pré-aprovadas com CCB emitida, operações via Open Finance e portabilidades de crédito (Resolução CMN nº 4.292/2013).
    * **`sqs-standard-lane-queue` (30% da capacidade do legado / 54 TPS)**: Propostas convencionais e de canais padrão.

### 4.3. Estágio 3: Back-Office, Averbação e Liquidação Assíncrona

* **`Worker-Fleet` (ECS Fargate / Java 21)**:
  * Consome as filas SQS via Virtual Threads e gerencia a comunicação com o Core Banking e com os gateways dos órgãos pagadores (Dataprev, eSocial, eConsig).
  * **Resilience4j Rate Limiter**: Trava estrita para garantir que a vazão repassada ao sistema legado nunca ultrapasse **180 TPS**.
* **`Aurora PostgreSQL` & Transactional Outbox Pattern**:
  * Após confirmação de averbação da margem no órgão pagador, o Worker atualiza a tabela `proposal` para status `AVERBED` e insere atomicamente um evento na tabela `proposal_event`.
* **`Settlement-Engine`**:
  * Lê os eventos de outbox via CDC e aciona o barramento de pagamentos do banco para efetuar o desembolso do crédito via **PIX ou TED** em D+0.
  * Notifica a infraestrutura de assinatura digital para chancelar definitivamente a CCB com a confirmação da margem averbada.


## 5. Análise Crítica de Trade-offs e Proteção de FinOps

| Riscos de Arquitetura Síncrona (Abordagem Tradicional) | Solução da Arquitetura Alvo Estagiada (3 Fases) |
| :--- | :--- |
| **Gargalo de Latência**: Rodar IA, Open Finance e KYC síncronos a 10.000 TPS derruba a vazão para < 50 TPS. | **Desacoplamento Temporal**: IA, Open Finance e KYC rodam no Estágio 1 (Off-pico). O Estágio 2 apenas absorve o token pré-aprovado a < 100 ms. |
| **Explosão de Custos (FinOps)**: Consultas de biometria e KYC cobradas por chamada seriam multiplicadas em 3 milhões de envios. | **Filtragem Atômica**: O DynamoDB bloqueia 499 envios duplicados na entrada por **R$ 0,00 em chamadas de API externas**. |
| **Ponto Único de Falha Externo**: Queda do Open Finance dos concorrentes às 00:01 paralisaria o consignado do banco. | **Zero Dependência Externa no Pico**: No momento 00:01, a Ingestion API depende unicamente da infraestrutura AWS (DynamoDB/SQS). |
| **Risco Jurídico de CCB**: Emitir CCB antes de garantir a margem gera necessidade de cancelamento se a margem for tomada por outro banco. | **Liquidação Condicional**: A CCB é pré-formalizada no Estágio 1, mas sua assinatura definitiva e desembolso só ocorrem no Estágio 3 **após confirmação da averbação**. |


## 6. Contratos de Interface e Schemas de Dados

### 6.1. Contrato da Ingestion API (`POST /v1/proposals`)

```json
{
  "title": "ProposalIngestionRequest",
  "type": "object",
  "required": ["idempotencyKey", "tenantId", "authToken", "agreementType", "amount", "installments"],
  "properties": {
    "idempotencyKey": {
      "type": "string",
      "description": "Chave unica gerada no Estagio 1 para idempotencia."
    },
    "tenantId": {
      "type": "string",
      "description": "Identificador do parceiro LaaS para controle de rate limit."
    },
    "authToken": {
      "type": "string",
      "description": "Token assinado do Estagio 1 com hash da CCB."
    },
    "agreementType": {
      "type": "string",
      "description": "Tipo de convenio de destino."
    },
    "amount": {
      "type": "number",
      "description": "Valor solicitado da operacao."
    },
    "installments": {
      "type": "integer",
      "description": "Quantidade de parcelas."
    }
  }
}
```

### 6.2. Schema DynamoDB (`DurableInbox`)

* **Partition Key (PK)**: `IDEMPOTENCY#<idempotencyKey>`
* **Sort Key (SK)**: `METADATA#<tenantId>`
* **Atributos**: `AuthToken`, `AgreementType`, `Amount`, `Status`, `PriorityLane`, `TTL`.

### 6.3. Script DDL Relacional (PostgreSQL)

```sql
CREATE TABLE IF NOT EXISTS tb_proposal (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    tenant_id VARCHAR(50) NOT NULL,
    tax_id VARCHAR(14) NOT NULL,
    agreement_type VARCHAR(30) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    installments INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_proposal_event (
    event_id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES tb_proposal(id),
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    processed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```


## 7. Telemetria e Dashboard Executivo (Prometheus / Grafana)

Para acompanhamento do C-Level e das equipes de Engenharia, o sistema exporta os seguintes indicadores em tempo real:

### 7.1. Painel Superior: Métricas de Negócio e Faturamento
* **VGV Ingerido vs. Averbado**: Volume financeiro (em R$) retido nos 5 minutos da virada e liquidado no legado.
* **QoS Distribution Ratio**: Proporção de propostas direcionadas para a **Fast Lane (70%)** vs. **Standard Lane (30%)**.
* **Open Finance & CCB Conversion Rate**: Percentual de propostas qualificadas pelo Agente IA que converteram em averbações bem-sucedidas.

### 7.2. Painel Inferior: Métricas de Engenharia, Resiliência e FinOps
* **Throughput de Ingestão (`ingestion_accepted_rate`)**: Métrica comprovando a absorção de até **10.000 TPS** com **p95 < 100 ms**.
* **Trava do Legado (`legacy_throughput_tps`)**: Linha reta demonstrando que o tráfego no Core Banking permanece cravado em estritamente **180 TPS**.
* **Economia por Idempotência (`dynamodb_duplicate_blocked_total`)**: Contador de requisições duplicadas bloqueadas atômica e gratuitamente na borda.
* **Effective Drain Time (`QueueDepth / 180 TPS`)**: Tempo preditivo exibido no painel indicando o tempo necessário para zerar o backlog da virada.

---

## 8. Critérios de Aceite para Entrada em Produção

A plataforma estará pronta para entrada em produção após o cumprimento dos seguintes requisitos automatizados na esteira de CI/CD:

1. **Teste de Carga k6 (Sustentado)**: Envio de 3.000.000 de propostas durante 5 minutos a 10.000 TPS com > 99,9% de respostas HTTP 202 em p95 < 100 ms.
2. **Proteção Rígida do Legado**: Comprovação via Grafana de que o Mock/Core Legado não recebeu mais de 180 TPS em nenhuma janela de 1 segundo.
3. **Teste de Idempotência FinOps**: Envio de 50.000 requisições concorrentes duplicadas resultando em exatamente 1 registro gravado no Aurora PostgreSQL e 0 chamadas extras a APIs pagas.
4. **Isolamento Multi-Tenant**: Resposta HTTP 429 para tráfego excessivo por um único correspondente, sem afetar o SLA de outros parceiros LaaS na Fast Lane.

---
*Documento Target de Produção aprovado para desenvolvimento e apresentação ao Comitê Master de Engenharia.*


