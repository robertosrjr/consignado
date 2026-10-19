# Architecture & Compliance Rules: Directivas e Regras de Engenharia

**Projeto:** Plataforma LaaS, Agente IA & Engine de Alta Vazão para Crédito Consignado e PME  
**Documento:** Regras de Arquitetura e Guardrails de Conformidade (Architecture & Compliance Rules)  
**Status:** Mandatory for CI/CD Architecture Gate & Automated Validation  
**Versão:** 1.0.0-FINAL  
**Nível:** Staff / Principal Systems Engineer  

---

## 1. Diretiva Obrigatória de Conformidade Regulatória (Regulatory Rules)

Toda implementação de código, esteira de deploy ou serviço que componha a plataforma deve obedecer estrita e incondicionalmente às diretivas regulatórias vigentes do mercado financeiro brasileiro. Nenhuma requisição ou proposta pode avançar no pipeline de ingestão se violar qualquer um dos guardrails abaixo.

### 📜 RULE-REG-001: Trava de Margem Consignável (IN INSS nº 138/2022 e nº 172/2024)
* **Regra:** O teto de comprometimento da renda para operações de empréstimo consignado do INSS é fixado em estritamente **45% do benefício**, distribuído em:
  * **35%** para empréstimo pessoal consignado convencional.
  * **5%** para cartão de crédito consignado (RMC).
  * **5%** para cartão benefício consignado (RCC).
* **Guarda de Borda:** Propostas que excedam esses percentuais devem ser rejeitadas no Estágio 2 com `HTTP 422 Unprocessable Entity` antes da gravação no DynamoDB ou SQS.

### 📜 RULE-REG-002: Prazo Máximo de Amortização (IN INSS nº 138/2022, Art. 5º)
* **Regra:** O número máximo de parcelas mensais e sucessivas para operações de crédito consignado vinculadas ao INSS é de **84 parcelas**.
* **Guarda de Borda:** Qualquer proposta com `installments > 84` deve ser descartada em memória no motor de triagem rápida (`Fast-Fail Filter Engine`).

### 📜 RULE-REG-003: Trava Temporária DDB de 90 Dias (IN INSS nº 138/2022, Art. 8º)
* **Regra:** Benefícios recém-concedidos do INSS permanecem bloqueados para operações de empréstimo consignado por **90 dias** a contar da Data de Despacho do Benefício (DDB).
* **Exceção:** Operações solicitadas diretamente pela 1ª instituição financeira pagadora do benefício.
* **Guarda de Borda:** A Borda de Ingestão deve consultar o cache de lista negra em memória (Caffeine/Redis) e rejeitar imediatamente com código `ERR_INSS_DDB_LOCKED_90_DAYS` (`HTTP 422`).

### 📜 RULE-REG-004: Parametrização Transicional de Margem (MP nº 1.355/2026)
* **Regra:** O motor de simulação e triagem deve manter flexibilidade parametrizada para suportar a transição progressiva do teto de margem consignável de **45% para 40%** a partir do ciclo regulatório de 2027.

### 📜 RULE-REG-005: Governança do Consignado Privado / eSocial (MP nº 1.292/2025 & Lei nº 10.820/2003)
* **Regra:** Operações do Programa Crédito do Trabalhador (Consignado Privado CLT) exigem validação prévia do vínculo empregatício via eSocial (Evento S-1200 / Rubrica 9253) e verificação da garantia do FGTS Digital.

### 📜 RULE-REG-006: Portabilidade de Crédito via Open Finance (Res. CMN nº 4.292/13 & Res. Conjunta 15/2025)
* **Regra:** Operações originadas por portabilidade de crédito via Open Finance devem receber **prioridade máxima de roteamento (Multiplicador 2.0x)** na fila de mensagens para garantir a troca eletrônica do Documento de Débito Crédito (DDC) dentro do prazo regulatório.

### 📜 RULE-REG-007: Sanções e Combate ao Assédio Comercial (Autorregulação Febraban / ABBC)
* **Regra:** Correspondentes bancários (`X-Tenant-ID`) que apresentarem comportamento anômalo ou atingirem 20 pontos de sanção na Autorregulação devem ter seu acesso suspenso na borda com resposta imediata `HTTP 403 Forbidden`.

---

## 2. Regras Invioláveis de Engenharia e Borda (Engineering Rules)

### ⚡ RULE-ENG-001: Ingestão em Alta Vazão com SLA < 100 ms (Estágio 2)
* **Regra:** A Ingestion API (Java 21 / Virtual Threads) deve responder à requisição de ingestão com código `HTTP 202 Accepted` em **p95 < 100 ms** e **p99 < 200 ms** sob carga de pico de **10.000 TPS**.
* **Proibição:** É estritamente proibido realizar chamadas síncronas a bancos de dados relacionais (PostgreSQL/Oracle) ou APIs externas durante a execução do endpoint `POST /v1/proposals`.

### 🛡️ RULE-ENG-002: Smart Idempotency por Hash de Negócio (Durable Inbox)
* **Regra:** A idempotência de borda deve ser calculada deterministicamente através da assinatura SHA-256 dos dados imutáveis do contrato:
  $$\text{BusinessHash} = \text{SHA256}(\text{CPF} \mid \text{Convenio} \mid \text{Valor} \mid \text{Parcelas} \mid \text{DataUTC})$$
* **Comportamento:** A gravação na tabela `DurableInbox` do DynamoDB deve utilizar a instrução condicional `attribute_not_exists(PK)`. Se um robô retransmitir a mesma proposta com novos UUIDs de cabeçalho, a requisição duplicada deve ser rejeitada atômica e silenciosamente em **< 15 ms** com status `HTTP 202` e header `X-Idempotency-Status: DUPLICATE_REJECTED`.

### 🚀 RULE-ENG-003: Camada de Triagem Rápida em Memória (Fast-Fail Filter Engine)
* **Regra:** Validações de elegibilidade regulatória devem rodar em memória (**Caffeine Cache / ElastiCache Redis**) em **< 5 ms**. Propostas inelegíveis não podem ser salvas no DynamoDB e não podem ser enfileiradas no SQS.
* **Formato de Erro:** Toda rejeição na triagem rápida deve retornar no padrão **RFC 7807 Problem Details** com código `HTTP 422`.

### 🔀 RULE-ENG-004: Partition Salting no Amazon DynamoDB
* **Regra:** Para evitar *Hot Partitions* durante o pico de 10.000 TPS, a Partition Key (`PK`) da tabela `DurableInbox` deve incorporar um sufixo de sharding distribuído entre 10 partições físicas:
  $$\text{PK} = \text{HASH}\#\text{BusinessHash}\#\text{PARTITION\_}(i \pmod {10})$$

### 🎯 RULE-ENG-005: Governança de QoS e Respeito ao Limite do Legado (180 TPS)
* **Regra:** A vazão global repassada ao sistema legado e órgãos pagadores (Dataprev/eSocial) nunca pode ultrapassar **180 TPS** em nenhuma janela de 1 segundo.
* **Alocação de Orçamento (Capacity Budgeting):**
  * **Ultra Fast Lane (SQS):** 50% do orçamento (90 TPS) para operações com Score $\ge$ 80.
  * **Fast Lane (SQS):** 30% do orçamento (54 TPS) para operações com Score entre 50 e 79.
  * **Standard Lane (SQS):** 20% do orçamento (36 TPS) para operations com Score < 50.

### ⏱️ RULE-ENG-006: Exposição Transparente de SLA Preditivo
* **Regra:** Todo retorno `HTTP 202 Accepted` deve injetar os cabeçalhos `X-Estimated-Drain-Time-Sec` e `X-Queue-Position-Estimate`, informando o tempo estimado de liquidação calculado via:
  $$\text{DrainTime}(t) = \frac{Q_{\text{lane}}(t) + P_{\text{relative}}}{\mu_{\text{lane\_eff}}(t)} + \Delta t_{\text{ext}}$$

---

## 3. Matriz de Validação Automatizada para o Architecture Gate (CI/CD)

Esta matriz define os testes obrigatórios que a esteira de integração contínua (CI/CD) deve executar para aprovar um deploy:

| Código da Regra | Tipo de Validador Automatizado | Critério do Architecture Gate |
| :--- | :--- | :--- |
| **RULE-REG-001** | Teste de Integração / Fast-Fail Filter | Submissão de proposta com margem de 46% falha com HTTP 422 em < 5 ms. |
| **RULE-REG-002** | Teste Unitário / Rule Engine | Submissão com 85 parcelas falha com `ERR_EXCEEDED_MAX_INSTALLMENTS`. |
| **RULE-REG-003** | Teste de Integração / In-Memory Cache | CPF com DDB < 90 dias cadastrado no Redis retorna HTTP 422 imediatamente. |
| **RULE-ENG-001** | Teste de Carga k6 (10.000 TPS) | p95 da Ingestion API permanece < 100 ms durante janela de 5 minutos. |
| **RULE-ENG-002** | Teste de Concorrência k6 | Disparo de 50.000 reqs com mesmo payload e UUIDs diferentes gera exato 1 registro. |
| **RULE-ENG-004** | Análise de Telemetria / AWS CloudWatch | Gravações de 10.000 TPS no DynamoDB apresentam desvio padrão < 15% entre shards. |
| **RULE-ENG-005** | Teste de Carga End-to-End | Medição no Mock Legado comprova vazão cravada em $\le$ 180 TPS globais. |

---

📄 **Documento publicado e homologado para a esteira de arquitetura.**  
*Autorização de Engenharia: Staff / Principal Systems Engineer.*
