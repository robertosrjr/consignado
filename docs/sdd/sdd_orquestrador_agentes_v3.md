# Software Design Document (SDD): Sistema Orquestrador de Agentes de IA Corporativo e Esteira de Auto-Remediação (Self-Healing)

> Nota de escopo — revisão de 16/10/2026: Este documento descreve a evolução do orquestrador de agentes de IA, um subsistema distinto dos serviços Java de consignado. Consulte o [índice documental](../README.md) e as limitações atuais antes de usar este texto como descrição do ambiente.


**Versão:** 3.0  
**Status:** Aprovado para Construção  
**Escopo:** Arquitetura Multi-Agente Heterogênea, Validação Adversarial (Maker-Checker), Guardião de Regras de Negócio (RAG), Auto-Remediação em CI/CD e Governança Corporativa  

---

## 1. Visão Geral e Princípios de Design

### 1.1 Propósito
Este documento especifica a arquitetura técnica, contratos de interface, roteamento de modelos heterogêneos e padrões de execução para a implementação do **Sistema Orquestrador de Agentes de IA Corporativo**, englobando a **Esteira de Revisão de Código (SDLC)** e o subsistema de **Auto-Remediação de Pipelines (Self-Healing)**.

O objetivo fundamental é fechar a lacuna de confiança (*Trust Gap*) entre a inteligência probabilística generativa e a execução operacional crítica em ambientes corporativos. O sistema combina análise probabilística distribuída entre múltiplos modelos heterogêneos com controle determinístico de infraestrutura, garantindo resiliência, alinhamento às regras de negócio e prevenção total contra viés de automodelagem.

### 1.2 Princípios Arquiteturais Fundamentais
* **Separação Rígida entre Inteligência e Controle:** O Modelo de Linguagem (LLM) atua exclusivamente como motor de raciocínio. Decisões de autorização, acesso a ferramentas, mutações no código e promoção de ambientes são gerenciadas e auditadas pelo Plano de Controle (*Control Plane*).
* **Roteamento de LLMs Heterogêneas & Validação Adversarial (*Maker-Checker*):** Para evitar o viés de automodelagem (*Self-Confirmation Bias*) — onde o mesmo modelo altera os testes para aprovar sua própria gambiarra —, o agente que corrige o código (**LLM A**) é obrigatoriamente distinto do agente que valida as regras de negócio (**LLM C**) e do agente que escreve os testes de regressão (**LLM B**).
* **Guardião de Regras de Negócio (Domínio & RAG):** A correção técnica do erro não pode violar a especificação do produto. O sub-agente de negócio valida o patch contra a base de conhecimento de domínio da empresa (OpenAPI/Swagger, DDD, docs de produto via RAG) antes que qualquer teste seja gerado.
* **Delegação com Contexto Isolado:** O agente orquestrador gerencia sub-agentes especialistas via invocação de ferramentas (*tool calling*). Cada especialista é executado em uma sessão isolada contendo apenas o contexto necessário para sua tarefa específica, sem expor a cadeia de raciocínio (*Think Chain*) dos outros agentes.
* **Arquitetura Orientada a Eventos (Reativa):** O orquestrador permanece em estado ocioso (*idle*) e é estimulado automaticamente por gatilhos de falha de CI/CD (Webhooks/Serverless Functions) para iniciar rotinas de auto-remediação.
* **Política de Acesso Restrito (*Deny-by-Default*):** Nenhuma ferramenta ou ação mutável no repositório ou infraestrutura é executada sem validação prévia e explícita pelo Ponto de Aplicação de Política (PEP) utilizando políticas Cedar/IAM.
* **Autonomia Graduada com *Human-in-the-Loop*:** Correções, testes de regressão e implantações em ambientes de Desenvolvimento (DEV) e Homologação (STAGING) operam de forma autônoma (Tiers T1–T3). A promoção final para **Produção (PROD)** exige validação humana obrigatória (*Human-in-the-Loop*) ou aprovação por *Quality Gate* determinístico estrito.

---

## 2. Arquitetura em 3 Planos e Matriz de Roteamento de Modelos

### 2.1 Matriz de Roteamento de Modelos Heterogêneos (Model Routing)

| Papel do Agente | Modelo / Provedor Recomendado | Justificativa Técnica e Função |
| :--- | :--- | :--- |
| **DiagnosticWorker** | *Fast/Cost-Effective* (GPT-4o-mini / Claude 3.5 Haiku) | Baixa latência e custo reduzido para parsing de stack trace, isolamento de arquivos e extração de logs de erro. |
| **DevFixWorker (LLM A)** | *Frontier Reasoning* (Claude 3.5 Sonnet / GPT-4o) | Raciocínio profundo e alta capacidade de codificação para analisar a causa raiz e gerar o patch de correção (*diff*). |
| **BusinessDomainWorker (LLM C)** | *Domain Specialist + RAG* (GPT-4o / Claude 3.5 Sonnet + Vetorizador) | RAG sobre regras de negócio (OpenAPI/Swagger/DDD). Valida se a correção técnica não quebra o comportamento funcional do produto. |
| **QARegressionWorker (LLM B)** | *Adversarial Coding* (DeepSeek Coder v2 / Qwen 2.5 Coder / GPT-4o-mini) | Modelo diferente de LLM A atuando como *Checker*. Escreve suítes de testes de unidade/integração para provar a eficácia e pegar regressões. |
| **DeploymentPromotionWorker** | *Deterministic Pipeline / Python CLI* | Execução sem LLM. Interage com Git, motores de CI/CD e gera a requisição de aprovação em Produção com log imutável. |

### 2.2 Plano de Execução (Runtime)
* **Laço ReAct Dinâmico:** Cada sub-agente executa a cadeia de raciocínio `Think -> Plan -> Act -> Observe -> Score`.
* **Motor de Reativação por Eventos:** Escuta requisições HTTP/Webhooks do CI/CD com dados da falha (`pipeline_id`, `commit_hash`, `error_log_raw`).
* **Isolamento de Sessão & Contexto:** O Agente B (QA) não recebe o histórico de pensamento do Agente A (Fix), garantindo que os testes sejam criados de forma independente e imparcial.

### 2.3 Plano de Controle (Control Plane & Governança)
* **Ponto de Aplicação de Política (PEP):** Intercepta chamadas de ferramentas, consultando políticas Cedar/IAM antes de autorizar escritas, commits ou chamadas de deploy.
* **Input & Output Guardrails:** Sanitizam logs de erro contra injeção de prompt indireta e mascaram segredos/PII nas saídas.

### 2.4 Plano de Ciclo de Vida (LLMOps / SDLC)
* **Quality Gates Determinísticos:** Regras condicionais rígidas que exigem 100% de aprovação na suíte de testes antes de autorizar a promoção para Homologação.
* **Trilha de Auditoria Imutável:** Registro detalhado registrando: Diagnóstico, Diff da LLM A, Parecer de Negócio da LLM C, Testes da LLM B, logs de execução do container e aprovação humana.

---

## 3. Design Detalhado dos Componentes do Agente de Auto-Remediação

### 3.1 Orquestrador de Auto-Remediação (`SelfHealingOrchestrator`)
* **Gatilho:** Invocado via Webhook por falha na esteira de CI/CD.
* **Catálogo de Ferramentas Nativas:**
  * `delegar(agente_alvo, instrucao, contexto)`: Invoca sub-agentes em sessão isolada com o modelo configurado.
  * `registrar_registro_tarefa(task_id, status)`: Mantém o estado dinâmico da execução (*Task Ledger*).
  * `notificar_equipe(canal, mensagem, severidade)`: Envia alertas para Slack/Teams sobre falhas e pedidos de aprovação em Produção.

### 3.2 Sub-Agentes Especialistas da Esteira de Remediação

1. **Agente de Diagnóstico (`DiagnosticWorker`)**
   * *Modelo:* Fast Model (ex: GPT-4o-mini)
   * *Função:* Processa a stack trace, identifica arquivos e funções afetadas, e resume o ponto exato de quebra.

2. **Agente Desenvolvedor (`DevFixWorker` - LLM A)**
   * *Modelo:* Frontier Code Model (ex: Claude 3.5 Sonnet)
   * *Função:* Elabora o patch técnico (*code diff*) focado em sanar a causa raiz do erro.

3. **Agente Guardião de Regras de Negócio (`BusinessDomainWorker` - LLM C)**
   * *Modelo:* Domain Reasoning + RAG (ex: GPT-4o + VectorDB)
   * *Função:* Submete o patch proposto contra as especificações funcionais e documentos de arquitetura da empresa (OpenAPI, Swagger, Markdown de requisitos). Se o patch quebrar uma regra de negócio, rejeita com apontamento justificativo para refazer a correção.

4. **Agente de QA e Testes de Regressão (`QARegressionWorker` - LLM B)**
   * *Modelo:* Adversarial Code Model (ex: DeepSeek Coder / Qwen 2.5 Coder)
   * *Função:* Escreve testes de unidade/integração (pytest/unittest) para reproduzir a falha original e validar a correção sem viés.

5. **Agente de Implantação e Promoção (`DeploymentPromotionWorker`)**
   * *Modelo:* Deterministic Runner / CLI
   * *Função:* Executa commit na branch `fix/auto-healing-*`, promove para Staging (Tier T3) e gera solicitação formal de aprovação para Produção com relatório completo (Tier T2).

---

## 4. Contratos de Interface e Schemas

### 4.1 Evento de Falha da Esteira (`PipelineFailureEventSchema`)
```json
{
  "title": "PipelineFailureEventSchema",
  "type": "object",
  "properties": {
    "event_id": { "type": "string" },
    "pipeline_id": { "type": "string" },
    "repository_url": { "type": "string" },
    "branch": { "type": "string" },
    "commit_hash": { "type": "string" },
    "environment": { "type": "string" },
    "failure_stage": { "type": "string" },
    "error_log_raw": { "type": "string" },
    "triggered_at": { "type": "string" }
  },
  "required": ["event_id", "pipeline_id", "repository_url", "branch", "commit_hash", "error_log_raw"]
}
```

### 4.2 Parecer de Negócio (`BusinessDomainCheckContract`)
```json
{
  "title": "BusinessDomainCheckContract",
  "type": "object",
  "properties": {
    "check_id": { "type": "string" },
    "approved": { "type": "boolean" },
    "domain_violations": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "rule_id": { "type": "string" },
          "specification_source": { "type": "string" },
          "description": { "type": "string" },
          "severity": { "type": "string", "enum": ["WARNING", "BLOCKING"] }
        },
        "required": ["rule_id", "description", "severity"]
      }
    },
    "guidance_for_dev_agent": { "type": "string" }
  },
  "required": ["check_id", "approved"]
}
```

### 4.3 Relatório Final de Auto-Remediação (`SelfHealingReportSchema`)
```json
{
  "title": "SelfHealingReportSchema",
  "type": "object",
  "properties": {
    "healing_id": { "type": "string" },
    "pipeline_id": { "type": "string" },
    "root_cause_analysis": {
      "type": "object",
      "properties": {
        "error_type": { "type": "string" },
        "affected_files": { "type": "array", "items": { "type": "string" } },
        "description": { "type": "string" }
      },
      "required": ["error_type", "affected_files", "description"]
    },
    "business_domain_approval": {
      "type": "object",
      "properties": {
        "approved": { "type": "boolean" },
        "checked_specifications": { "type": "array", "items": { "type": "string" } }
      },
      "required": ["approved"]
    },
    "patch_applied": {
      "type": "object",
      "properties": {
        "branch_created": { "type": "string" },
        "diff_content": { "type": "string" }
      },
      "required": ["branch_created", "diff_content"]
    },
    "regression_tests": {
      "type": "object",
      "properties": {
        "test_files_added": { "type": "array", "items": { "type": "string" } },
        "execution_status": { "type": "string", "enum": ["PASSED", "FAILED"] },
        "coverage_percentage": { "type": "number" }
      },
      "required": ["test_files_added", "execution_status"]
    },
    "promotion_status": {
      "type": "object",
      "properties": {
        "dev_status": { "type": "string" },
        "staging_status": { "type": "string" },
        "prod_promotion_request": {
          "type": "object",
          "properties": {
            "required_approval": { "type": "boolean" },
            "approval_status": { "type": "string", "enum": ["PENDING", "APPROVED", "REJECTED"] },
            "approver_id": { "type": "string" }
          },
          "required": ["required_approval", "approval_status"]
        }
      },
      "required": ["dev_status", "staging_status", "prod_promotion_request"]
    }
  },
  "required": ["healing_id", "pipeline_id", "root_cause_analysis", "business_domain_approval", "patch_applied", "regression_tests", "promotion_status"]
}
```

---

## 5. Diagrama de Sequência e Fluxo Operacional

```text
[CI/CD Event] ──► Webhook Trigger
                        │
                        ▼
            [OrchestratorAgent]
                        │
      ┌─────────────────┼─────────────────┬─────────────────┐
      │ (1. Diagnose)   │ (2. Technical)  │ (3. Business)   │ (4. Test)
      ▼                 ▼                 ▼                 ▼
[DiagnosticWorker]  [DevFixWorker]   [BusinessDomain]  [QARegression]
 (GPT-4o-mini)      (LLM A: Sonnet)  (LLM C + RAG)     (LLM B: DeepSeek)
      │                 │                 │                 │
      └─ Stack Trace ──►├─ Code Patch ───►│                 │
                        │                 ├─ Validate Spec ─┤
                        │                 │  (If Violates:  │
                        │                 │   Rejects Patch)│
                        │                 │                 ├─ Write Tests ──► [Quality Gate]
                        │                 │                 │                  (Container Runner)
                        │                 │                 │                         │
                        │                 │                 │                         ├── IF PASSED
                        │                 │                 │                         ▼
                                                                             [DeploymentPromotionWorker]
                                                                                      │
                                                                       ┌──────────────┴──────────────┐
                                                                       ▼                             ▼
                                                                 Deploy Staging                 Request PROD
                                                                   (Tier T3)                   (Tier T2 - HITL)
```

---

## 6. Governança, Autonomia Graduada e Segurança

### 6.1 Matriz de Autonomia Graduada

| Nível (*Tier*) | Escopo de Ação Permitido | Regras de Governança e Portões de Segurança |
| :--- | :--- | :--- |
| **Tier T1 (Probatório)** | Leitura de logs e análise de causa raiz | Leitura estrita de logs de erro e código. Nenhuma alteração permitida. |
| **Tier T2 (Supervisionado)** | Promoção para **PRODUÇÃO** | Exige **aprovação humana obrigatória (*Human-in-the-Loop*)** ou *Quality Gate* determinístico estrito. |
| **Tier T3 (Confiável)** | Patch, Testes e Deploy em **DEV / STAGING** | Execução autônoma de criação de branch, fix, validação de regras de negócio, testes e deploy em Staging. |
| **Tier T4 (Autônomo)** | Autocorreção de infraestrutura leve em Staging | Restrito a ambientes de não-produção com auditoria assíncrona pós-execução. |

### 6.2 Travas Operacionais e Limites de Runtime
* `MAX_PASSOS`: 5 iterações ReAct por sub-agente.
* `MAX_HEALING_ATTEMPTS`: Máximo de 2 tentativas de correção automática por evento.
* `TOKEN_BUDGET_PER_HEALING`: 30.000 tokens distribuídos entre os modelos.
* `SANDBOX_ISOLATION`: O patch e os testes são compilados e executados em containers efémeros isolados sem acesso à rede externa.

---

## 7. Roteiro de Implementação

* **Fase 1 (Core ReAct & Diagnostic Worker):** Implementar o laço ReAct base com captura de logs de erro e identificação de causa raiz no código.
* **Fase 2 (DevFix, Business Domain & QA Workers):** Desenvolver a lógica do Agente Desenvolvedor (LLM A), do Guardião de Regras de Negócio com RAG (LLM C) e do Agente de QA Adversarial (LLM B).
* **Fase 3 (Gatilho por Evento & Staging Deploy):** Integrar o webhook de gatilho do CI/CD e a automação de commit, PR e implantação em ambiente de Homologação (STAGING - Tier T3).
* **Fase 4 (Governança PROD & Control Plane):** Adicionar a validação de política *Deny-by-Default* (PEP), a interface de aprovação humana (*Human-in-the-Loop*) para Produção (Tier T2) e o registro imutável na Trilha de Auditoria.
