# Governança de Arquitetura, Regras Normativas e Repositório de ADRs (MADR / IEEE 1016)

**Projeto:** Plataforma LaaS, Agente IA & Engine de Alta Vazão para Crédito Consignado e PME  
**Maturidade Arquitetural:** Staff / Principal Systems Engineer & Enterprise Governance Board  
**Status:** Approved for Pipeline Integration & Architecture Gate Validation  
**Padrão Documental:** MADR v3.0 (Markdown Architecture Decision Records), IEEE 1016-2009 & ISO/IEC/IEEE 42010  
**Versão:** 1.0.0-GOV  

---

## 1. Diretiva Executiva e Declaração Obrigatória de Compliance (Rules & Governance Directives)

### 1.1. Declaração Obrigatória de Conformidade Regulatória
Esta diretiva estabelece a **obrigatoriedade estrita e incondicional** de cumprimento de todas as normas jurídicas, resoluções bancárias, instruções normativas e ordenamentos regulatórios vigentes no Brasil para qualquer serviço, microsserviço, API, script ou componente de infraestrutura pertencente a esta plataforma.

> **Regra de Borda Nº 001 (Compliance Primário):** Nenhum contrato de crédito consignado, portabilidade ou retenção pode ser ingerido, enfileirado, averbado ou liquidado se violar, em qualquer ordem de grandeza ou fração de segundo, os limites de margem, prazos, travas temporárias e requisitos de segurança estipulados pelas autoridades governamentais (INSS, MTE, BCB, CMN, Dataprev, SERPRO, Caixa Econômica Federal) e entidades de autorregulação (Febraban / ABBC).

### 1.2. Integração com Esteira de Validação Automatizada (CI/CD Architecture Gate)
Todas as **Architecture Decision Records (ADRs)** contidas na Seção 3 deste documento foram estruturadas no padrão **MADR (Markdown Architecture Decision Records)** e munidas de **parâmetros matemáticos e normativos padronizados (Machine-Readable Metadata)**. 

A esteira de integração contínua (CI/CD Pipeline) e o orquestrador de testes de carga (**k6**) do projeto parceiro/integrador **DEVEM obrigatoriamente** validar os seguintes artefatos contra os limites expostos neste documento antes de aprovar qualquer *pull request* ou *deployment* em ambiente de homologação/produção:

1. **Gate 1 — Unit & Integration Test Check:** Validação dos métodos de sanitização de CPF, cálculo de hash SHA-256 de negócio, verificações de tabela de bloqueio DDB (90 dias) e fórmulas do Dynamic Score QoS.
2. **Gate 2 — Compliance Parameter Check:** Verificação das variáveis de configuração do motor de simulação (teto de margem 45% -> 40%, teto de 84 parcelas, taxa CET teto e autenticação OAuth 2.0 via certificado ICP-Brasil).
3. **Gate 3 — Load & Stress Testing Gate (k6):** Execução do teste de injeção massiva a 10.000 TPS por 5 minutos, exigindo p95 < 100 ms na borda, descarte atômico de duplicatas, barramento em memória < 5 ms para propostas inválidas e contenção estrita do legado em <= 180 TPS.

---

## 2. Resumo Executivo de Verificação da Exatidão das Regras nos Artefatos

O Comitê de Arquitetura e Engenharia realizou uma auditoria cruzada nos **15 artefatos técnicos** e nas **35 fontes regulatórias e tecnológicas** do projeto para atestar a exatidão matemática e legal das regras implementadas.

### 2.1. Matriz de Auditoria e Verificação de Exatidão

| Domínio de Regra / Parâmetro | Fonte / Norma de Origem | Parâmetro Exato Exigido | Status nos Artefatos da PoC | Status da Validação para Esteira CI/CD |
| :--- | :--- | :--- | :--- | :--- |
| **Margem Consignável INSS** | IN PRES/INSS nº 138/2022 | **45% total**: 35% empréstimo pessoal, 5% cartão RMC, 5% cartão benefício RCC. | Implementado na Ingestion API & Fast-Fail Filter Engine. | **APROVADO (100% Exato)** |
| **Prazo Máximo do INSS** | IN PRES/INSS nº 138/2022 (Art. 5º, VI) | **Máximo de 84 parcelas** mensais e sucessivas. | Validado no Guardrail 4 da Borda. | **APROVADO (100% Exato)** |
| **Trava DDB 90 Dias** | IN PRES/INSS nº 138/2022 (alt. IN 172/2024) | **Bloqueio de 90 dias** a partir da DDB para bancos não-originadores. | Validado em memória (< 5 ms) no Fast-Fail Filter Engine. | **APROVADO (100% Exato)** |
| **Escalonamento MP 1.355/2026** | Medida Provisória nº 1.355/2026 | Transição gradual do teto de margem de **45% para 40%** a partir de 2027. | Parametrizado no motor de regras em memória. | **APROVADO (100% Exato)** |
| **Crédito do Trabalhador (eSocial/FGTS)** | MP nº 1.292/2025 & Portaria MTE nº 240/2026 | Rubrica 9253 no eSocial (S-1200) e retenção DAE/FGTS Digital. | Mapeado no fluxo do Estágio 3 e SDD Target. | **APROVADO (100% Exato)** |
| **Portabilidade Open Finance** | Res. CMN nº 4.292/13 & Res. Conjunta CMN/BCB nº 15/2025 | Prazos reduzidos (até 3 dias) e priorização via Open Finance. | Mapeado na Fast/Ultra Fast Lane do Dynamic Score QoS. | **APROVADO (100% Exato)** |
| **Smart Idempotency (Hash)** | Stripe API & IETF Draft Standards | `SHA256(CPF|Convenio|Valor|Parcelas|DataUTC)` + `attribute_not_exists`. | Implementado na `DurableInbox` DynamoDB (rejeição < 15 ms). | **APROVADO (100% Exato)** |
| **Capacidade do Legado (Capacity Budget)** | Blueprint PoC & Especificação AI-DLC | Consumo rigoroso e estrito em **<= 180 TPS** (Resilience4j RateLimiter). | Trava no Worker Fleet + Mock Legacy. | **APROVADO (100% Exato)** |
| **Write Sharding (Partition Salting)** | AWS Database Best Practices | Sufixo `#PARTITION_(i % 10)` em 10 partições físicas DynamoDB. | Distribuição das 10.000 req/s em partições físicas NoSQL. | **APROVADO (100% Exato)** |
| **Previsão de SLA (Drain Time)** | Queueing Theory (M/M/c) & SRE Standards | \\(\text{DrainTime} = \frac{Q_{\text{lane}} + P_{\text{relative}}}{\mu_{\text{lane\_eff}}} + \Delta t_{\text{ext}}\\). | Resposta HTTP 202 com cabeçalho `X-Estimated-Drain-Time-Sec`. | **APROVADO (100% Exato)** |

---

## 3. Repositório de ADRs Matemáticas e Normativas (MADR Format)

---

### ADR-001: Padrão de Processamento Estagiado em 3 Fases (Desacoplamento Front/Back)

* **Status:** Aprovado
* **Data:** 2026-10-14
* **Decisores:** Comitê de Arquitetura de Plataforma & Staff Systems Engineers
* **Tags:** `architecture`, `decoupling`, `3-stage`, `concurrency`, `sla`

#### Contexto e Declaração do Problema
Operações de originação de crédito modernas exigem chamadas a serviços de IA (leitura contextual de propostas), análise de extratos via Open Finance, checagens de KYC/PLD (Circular BCB nº 3.978) e biometria facial (IN INSS 138/172). O tempo combinado de execução dessas chamadas síncronas varia de 3 a 15 segundos. No momento do pico da "virada de folha" (00:01), o volume atinge **10.000 TPS**. Manter o fluxo síncrono destruiria o SLA de borda (< 100 ms) e causaria colapso do sistema por exaustão de conexões HTTP e banco de dados.

#### Decisão Considerada e Aprovada
Isolar a solução em um **Padrão Estagiado em 3 Fases**:
1. **Estágio 1 (Pré-Originação Off-Pico / Durante o Dia):** Agente IA, Open Finance, KYC/PLD, Biometria e emissão da CCB Digital pré-aprovada (`READY_FOR_SUBMISSION`).
2. **Estágio 2 (Borda de Ingestão de Alta Vazão - 00:01):** Absorção atômica do payload leve em Java 21 / Virtual Threads com resposta `HTTP 202 Accepted` em < 100 ms a 10.000 TPS.
3. **Estágio 3 (Back-Office e Liquidação Assíncrona):** Consumo cadenciado pelos Workers Fargate a estritamente **180 TPS** com gravação relacional no Aurora PostgreSQL (Transactional Outbox) e pagamento Pix/TED em D+0.

#### Consequências
* **Positivas:** Ponto único de falha (SPOF) de parceiros externos zerado no momento do pico; SLA p95 < 100 ms garantido na borda; proteção total do Core Banking a 180 TPS.
* **Negativas:** Exige gerenciamento de estado assíncrono e comunicação de status via Webhook/SSE/SLA Preditivo com a aplicação parceira.

---

### ADR-002: Smart Idempotency por Hash SHA-256 de Negócio (Durable Inbox)

* **Status:** Aprovado
* **Data:** 2026-10-14
* **Decisores:** Equipe de Borda & Engenharia de Dados
* **Tags:** `idempotency`, `sha256`, `dynamodb`, `finops`, `bot-protection`

#### Contexto e Declaração do Problema
Robôs e scripts de correspondentes bancários ("pastinhas") retransmitem a mesma proposta centenas de vezes ao sofrerem timeouts momentâneos. No modelo tradicional de idempotência (Stripe / IETF), o cliente envia um UUID no cabeçalho HTTP (`Idempotency-Key`). Contudo, robôs mal programados geram um **novo UUID v4 a cada tentativa de retry**, contornando a idempotência e gerando gravações duplicadas, acúmulo no SQS e custos de FinOps desnecessários.

#### Decisão Considerada e Aprovada
Substituir a dependência do cabeçalho do cliente pela geração de uma **impressão digital determinística do contrato de crédito (Business Domain Fingerprint)** na borda da API:

$$\text{BusinessHash} = \text{SHA256}(\text{CPF} \mid \text{Convenio} \mid \text{Valor} \mid \text{Parcelas} \mid \text{DataUTC})$$

Gravar atomicamente no Amazon DynamoDB (`DurableInbox`) utilizando a instrução:
$$\text{PK} = \text{HASH}\#\text{BusinessHash}, \quad \text{ConditionExpression} = \text{"attribute\_not\_exists(PK)"}$$

#### Consequências e Parâmetros de Validação (CI/CD)
* **Tempo de Resposta em Duplicata:** $< 15\text{ ms}$ com status `HTTP 202 Accepted` e header `X-Idempotency-Status: DUPLICATE_REJECTED`.
* **Impacto no SQS/PostgreSQL:** Zero eventos gerados nas filas downstream para retentativas.
* **Custo FinOps:** Descarte gratuito na camada NoSQL em nível de partição.

---

### ADR-003: Engine de Triagem Rápida em Memória (Fast-Fail Filter Engine < 5ms)

* **Status:** Aprovado
* **Data:** 2026-10-14
* **Decisores:** Comitê de Compliance, Risco & SRE
* **Tags:** `fast-fail`, `in-memory`, `caffeine`, `redis`, `rfc7807`

#### Contexto e Declaração do Problema
No pico de 10.000 TPS, cerca de 20% a 30% das requisições enviadas por robôs são juridicamente ou regulatoriamente inelegíveis (ex: CPF com benefício INSS bloqueado por ter menos de 90 dias da DDB, estouro de margem consignável ou parceiro suspenso pela Autorregulação Febraban). Permitir que essas propostas cheguem às filas SQS e aos Workers gasta o orçamento de 180 TPS do legado com requisições fadadas ao insucesso no Dataprev.

#### Decisão Considerada e Aprovada
Embarcar na `Ingestion-API-Service` (Java 21) uma **Camada de Triagem Rápida de Regras em Memória (Caffeine Local Cache + Redis Read Replicas)** executando **5 Guardrails de Borda em < 5 ms**:

1. **Guardrail 1:** Validação da assinatura criptográfica do `preApprovedToken`.
2. **Guardrail 2:** Sanitização e algoritmo do dígito verificador do CPF.
3. **Guardrail 3 (IN INSS 138/172):** Consulta de trava temporária de 90 dias da DDB para bancos não-originadores.
4. **Guardrail 4 (MP 1.355/2026 & Portaria MTE 240):** Validação de teto de parcelas (84 meses INSS) e limites de margem (45% -> 40%).
5. **Guardrail 5 (Febraban/ABBC):** Bloqueio de `X-Tenant-ID` de parceiros suspensos por assédio comercial.

#### Consequências
* Propostas inelegíveis respondem em **< 5 ms** com status `HTTP 422 Unprocessable Entity` no padrão **RFC 7807 Problem Details**.
* Zero consumo de gravações no DynamoDB, zero mensagens no SQS e reserva de 100% da capacidade do legado (180 TPS) para propostas válidas.

---

### ADR-004: Algoritmo de Priorização Dinâmica (Dynamic Score QoS) & Escalonamento WFQ

* **Status:** Aprovado
* **Data:** 2026-10-14
* **Decisores:** Engenharia de Sistemas Distribuidos & Inteligência Financeira
* **Tags:** `qos`, `wfq`, `dynamic-score`, `sqs-lanes`, `capacity-budgeting`

#### Contexto e Declaração do Problema
Tratar todas as propostas com a mesma prioridade (*FIFO puro*) faz com que operações de altíssimo valor financeiro, portabilidades com janela de expiração curta ou clientes VIP fiquem retidos no final da fila de 3 milhões de mensagens, correndo o risco de perder a margem para bancos concorrentes.

#### Decisão Considerada e Aprovada
Implementar o algoritmo **Dynamic Score QoS** no *AWS Lambda Dispatcher*, calculando um score de prioridade de $0 \dots 100$ para cada proposta em < 3 ms:

$$\text{Score} = (W_1 \cdot \text{VGV}_{\text{norm}}) + (W_2 \cdot \text{Rating}_{\text{VIP}}) + (W_3 \cdot \text{RiscoConcorrencia} \cdot M_{\text{OpenFinance}})$$

* **Pesos Oficiais:** $W_1 = 0{,}40$, $W_2 = 0{,}35$, $W_3 = 0{,}25$.
* **Multiplicador de Portabilidade (Res. CMN 4292 / Res. Conjunta 15):** $M_{\text{OpenFinance}} = 2{,}0\times$.

#### Alocação de Orçamento no Worker Fleet (Total Legado: 180 TPS)
1. **Ultra Fast Lane ($\text{Score} \ge 80$):** $50\%$ da capacidade (**90 TPS**) — SLA < 2 min.
2. **Fast Lane ($50 \le \text{Score} < 80$):** $30\%$ da capacidade (**54 TPS**) — SLA < 10 min.
3. **Standard Lane ($\text{Score} < 50$):** $20\%$ da capacidade (**36 TPS**) — SLA < 45 min.

#### Mecanismo Anti-Starvation (Aging Factor)
Para evitar que propostas na fila Standard fiquem retidas indefinidamente (*Queue Starvation*), o score sofre um incremento temporal contínuo:
$$\text{Score}_{\text{efetivo}}(t) = \text{Score}_{\text{base}} + (\alpha \cdot \Delta t), \quad \text{com } \alpha = 0{,}10 \text{ pts/segundo}$$
Propostas com mais de 10 minutos de retenção são promovidas automaticamente para a Fast Lane.

---

### ADR-005: Engine de Previsão de SLA & Effective Drain Time

* **Status:** Aprovado
* **Data:** 2026-10-14
* **Decisores:** Time de Produto, APIs & Experiência do Parceiro
* **Tags:** `sla`, `drain-time`, `predictive-analytics`, `transparency`, `webhooks`

#### Contexto e Declaração do Problema
O retorno de um `HTTP 202 Accepted` sem estimativa de conclusão cria ansiedade no correspondente bancário, fazendo-o disparar robôs para re-submeter a proposta ou enviá-la simultaneamente para bancos concorrentes.

#### Decisão Considerada e Aprovada
Injetar no cabeçalho da resposta `HTTP 202 Accepted` da Ingestion API a métrica calculada do **Effective Drain Time**:

$$\text{DrainTime}(t) = \frac{Q_{\text{lane}}(t) + P_{\text{relative}}}{\mu_{\text{lane\_eff}}(t)} + \Delta t_{\text{ext}}$$

* $Q_{\text{lane}}(t)$: Profundidade instantânea do backlog da faixa no SQS.
* $P_{\text{relative}}$: Posição estimada da proposta baseada no Dynamic Score.
* $\mu_{\text{lane\_eff}}(t)$: Taxa efetiva de consumo dos Workers para aquela faixa ($90$, $54$ ou $36$ TPS).
* $\Delta t_{\text{ext}}$: Latência média p50 de resposta do gateway governamental (Dataprev/eSocial).

#### Exposição na API
* Cabeçalhos HTTP: `X-Estimated-Drain-Time-Sec: 185`, `X-Queue-Position-Estimate: 1420`.
* Objeto JSON:
```json
"slaPredictive": {
  "estimatedDrainTimeSeconds": 185,
  "estimatedCompletionTime": "2026-10-14T00:04:05Z",
  "confidenceIntervalPercentage": 98.5
}
```

---

### ADR-006: Write Sharding (Partition Salting) no Amazon DynamoDB

* **Status:** Aprovado
* **Data:** 2026-10-14
* **Decisores:** AWS Cloud Architects & SRE Lead
* **Tags:** `dynamodb`, `partition-salting`, `write-sharding`, `nosql-scaling`

#### Contexto e Declaração do Problema
Cada partição física do Amazon DynamoDB possui um limite estrito de **1.000 Write Capacity Units (WCU)** e **3.000 Read Capacity Units (RCU)**. Na injeção massiva de **10.000 TPS**, se a chave de partição concentrar tráfego em poucas partições físicas, o DynamoDB lançará exceções de `ProvisionedThroughputExceededException` (*Hot Partition*).

#### Decisão Considerada e Aprovada
Aplicar a técnica de **Write Sharding (Partition Salting)** recomendada na documentação técnica da AWS, sufixando a chave primária da tabela `DurableInbox` com um cálculo distribuído entre 10 partições físicas:

$$\text{PartitionSuffix} = i \pmod{10}, \quad \text{onde } i = \text{abs}(\text{hashCode}(\text{businessHash}))$$
$$\text{PK} = \text{HASH}\#\text{businessHash}\#\text{PARTITION\_}\text{PartitionSuffix}$$

#### Consequências
* A carga de 10.000 TPS de gravação é fatiada em exatos **1.000 TPS por partição física**, mantendo a operação perfeitamente dentro do envelope de segurança da AWS sem *throttling*.

---

### ADR-007: Parametrização Regulatória das Margens Consignáveis (INSS 138/172, MP 1.355/2026, MP 1.292/2025)

* **Status:** Aprovado
* **Data:** 2026-10-14
* **Decisores:** Diretoria de Risco de Crédito & Compliance Legal
* **Tags:** `regulatory`, `inss`, `mp1355`, `mp1292`, `esocial`, `fgts-digital`

#### Contexto e Declaração do Problema
O ecossistema de crédito consignado no Brasil sofreu revisões profundas no arcabouço legal entre 2022 e 2026. Motores de crédito desatualizados causam ofertas inválidas, rejeição em massa no eConsig/Dataprev e penalidades administrativas da Febraban/ABBC.

#### Decisão Considerada e Aprovada
Parametrizar de forma unificada as regras legais nos motores de simulação e triagem:

1. **Beneficiários INSS (IN PRES/INSS nº 138/2022 alt. IN nº 172/2024):**
   * Teto Global de Margem: **45%** ($35\%$ empréstimo pessoal, $5\%$ cartão RMC, $5\%$ cartão benefício RCC).
   * Prazos: Limite estrito de **84 parcelas mensais**.
   * Trava DDB 90 Dias: Bloqueio automático por 90 dias a contar da Data de Despacho do Benefício para bancos não-originadores.
   * Biometria Facial: Obrigatoriedade de validação biométrica antes da formalização.
2. **Servidores Públicos Federais (Medida Provisória nº 1.355/2026):**
   * Motor de regras parametrizado para o cronograma de redução da margem de **45% para 40%** a partir de 2027.
3. **Trabalhadores CLT / Crédito do Trabalhador (MP nº 1.292/2025 & Portaria MTE nº 240/2026):**
   * Retenção em folha via **eSocial** (evento S-1200 / rubrica 9253 com códigos de incidência `[31]`, `[00]`, `[9]`).
   * Garantia do saldo de demissão via **FGTS Digital** e repasses intermediados pela Caixa Econômica Federal e SERPRO.

---

### ADR-008: Segurança Criptográfica ICP-Brasil, OAuth 2.0 e Rastreabilidade LGPD/Open Finance

* **Status:** Aprovado
* **Data:** 2026-10-14
* **Decisores:** Chief Information Security Officer (CISO) & Data Privacy Officer (DPO)
* **Tags:** `security`, `icp-brasil`, `oauth2`, `lgpd`, `open-finance`, `cmn-15`

#### Contexto e Declaração do Problema
As integrações com plataformas governamentais (Dataprev, SouGov, eConsig, eSocial, Prodesp/SCC) e com o ecossistema do Open Finance exigem padrões de autenticação de infraestrutura crítica com validade jurídica extrajudicial (CCB via licença SCD).

#### Decisão Considerada e Aprovada
1. **Autenticação de Borda:** Exigência de mTLS e tokens **OAuth 2.0 com Certificado Digital ICP-Brasil** de pessoa jurídica.
2. **Gestão de Segredos:** Armazenamento de credenciais e certificados no **AWS Secrets Manager** com rotação e alarmes de expiração em 30 dias.
3. **Open Finance (Res. CMN nº 4.292/2013 & Res. Conjunta CMN/BCB nº 15/2025):** Emissão de Demonstrativo Descritivo do Crédito (DDC) com rastreabilidade de consentimento auditável (timestamp, IP, versão do termo e canal).
4. **Proteção LGPD:** Criptografia de dados sensíveis em trânsito (TLS 1.3) e em repouso (AWS KMS com chaves de cliente), além de mascaramento de CPF (`XXX.456.789-XX`) nos logs estruturados do OpenTelemetry.

---

## 4. Conclusão e Diretriz de Validação para a Esteira do Outro Projeto

Este documento de Governança e Repositório de ADRs é a **fonte da verdade técnica e regulatória** do projeto. 

As especificações em código e nos arquivos de teste do projeto parceiro **DEVEM** importar e testar esses parâmetros. A esteira de CI/CD considerará a *build* **APROVADA** apenas quando todos os 3 Gates de validação forem satisfeitos sem exceções.

```
┌────────────────────────────────────────────────────────────────────────┐
│               PIPELINE DE VALIDAÇÃO DE ARQUITETURA & CI/CD              │
│                                                                        │
│   [ Pull Request / Commit ]                                            │
│               │                                                        │
│               ▼                                                        │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │ GATE 1: Validation Rules & SHA256 Hash (ADR-002 / ADR-003)     │   │
│   └───────────────────────────────┬────────────────────────────────┘   │
│                                   │ (Pass)                             │
│                                   ▼                                    │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │ GATE 2: Compliance Rules & Limits INSS/MP1355 (ADR-007 / 008)  │   │
│   └───────────────────────────────┬────────────────────────────────┘   │
│                                   │ (Pass)                             │
│                                   ▼                                    │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │ GATE 3: k6 Stress Test Gate (10.000 TPS Borda / 180 TPS Core)  │   │
│   │ (ADR-001 / ADR-004 / ADR-005 / ADR-006)                        │   │
│   └───────────────────────────────┬────────────────────────────────┘   │
│                                   │ (Pass)                             │
│                                   ▼                                    │
│               [ BUILD APPROVED FOR PRODUCTION DEPLOY ]                 │
└────────────────────────────────────────────────────────────────────────┘
```

---
*Documento emitido e chancelado pelo Comitê de Governança de Arquitetura Corporativa.*
