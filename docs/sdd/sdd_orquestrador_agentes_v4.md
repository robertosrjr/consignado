# Software Design Document (SDD): Sistema Orquestrador de Agentes de IA Corporativo e Esteira de Auto-Remediação (Self-Healing)

> Nota de escopo — revisão de 16/10/2026: Este documento descreve a evolução do orquestrador de agentes de IA, um subsistema distinto dos serviços Java de consignado. Consulte o [índice documental](../README.md) e as limitações atuais antes de usar este texto como descrição do ambiente.


**Versão:** 4.0  
**Status:** Aprovado para Construção & Produção  
**Escopo:** Arquitetura Multi-Agente Heterogênea, Validação Adversarial (Maker-Checker), Guardião de Regras de Negócio Dual-Mode (Contratos + RAG), Engenharia de Resiliência de CI/CD (Flaky Test Gate, Error State Hashing, Fast-Pathing, Taint Control) e Governança Corporativa  

---

## 1. Visão Geral e Princípios de Design

### 1.1 Propósito
Este documento especifica a arquitetura técnica, contratos de interface, roteamento de modelos heterogêneos, engenharia de resiliência e padrões de execução para a implementação do **Sistema Orquestrador de Agentes de IA Corporativo**, englobando a **Esteira de Revisão de Código (SDLC)** e o subsistema de **Auto-Remediação de Pipelines (Self-Healing)**.

O objetivo fundamental é fechar a lacuna de confiança (*Trust Gap*) entre a inteligência probabilística generativa e a execução operacional crítica em ambientes corporativos. O sistema combina análise probabilística distribuída entre múltiplos modelos heterogêneos com controle determinístico de infraestrutura, garantindo resiliência, alinhamento às regras de negócio e prevenção total contra viés de automodelagem e falhas de runtime.

### 1.2 Princípios Arquiteturais Fundamentais
* **Separação Rígida entre Inteligência e Controle:** O Modelo de Linguagem (LLM) atua exclusivamente como motor de raciocínio. Decisões de autorização, acesso a ferramentas, mutações no código e promoção de ambientes são gerenciadas e auditadas pelo Plano de Controle (*Control Plane*).
* **Roteamento de LLMs Heterogêneas & Validação Adversarial (*Maker-Checker*):** Para evitar o viés de automodelagem (*Self-Confirmation Bias*) — onde o mesmo modelo altera os testes para aprovar sua própria gambiarra —, o agente que corrige o código (**LLM A**) é obrigatoriamente distinto do agente que valida as regras de negócio (**LLM C**) e do agente que escreve os testes de regressão (**LLM B**).
* **Guardião de Regras de Negócio Dual-Mode (Contratos vs. RAG):** 
  * *Modo Universal (Sem Docs):* Atua na **Garantia de Integridade de Contratos e Análise de Impacto Estrutural**, impedindo alterações incompatíveis em APIs, remoção de campos obrigatórios e supressão de testes antigos.
  * *Modo Avançado (Com Docs/BDD):* Ativa o **RAG de Domínio** sobre arquivos de requisitos (BDD `.feature`, OpenAPI/Swagger, Markdown) para garantir conformidade funcional.
* **Resiliência Prática de CI/CD & FinOps:** Implementação obrigatória de **Portão Anti-Flaky Test**, **Error State Hashing** (prevenção contra loops semânticos de oscilação $Bug A \leftrightarrow Bug B$), **Triagem em Cascatas (Fast-Pathing)** e **Sanitização de Logs (Taint Control)** contra Prompt Injection indireto.
* **Delegação com Contexto Isolado:** O agente orquestrador gerencia sub-agentes especialistas via invocação de ferramentas (*tool calling*). Cada especialista é executado em uma sessão isolada contendo apenas o contexto necessário para sua tarefa específica.
* **Política de Acesso Restrito (*Deny-by-Default*) & Isolation via Git Worktrees:** Nenhuma ação mutável é executada diretamente no branch principal. Toda alteração ocorre em `git worktree` efémero com validação de `HEAD_COMMIT`.
* **Autonomia Graduada com *Human-in-the-Loop*:** Correções, testes de regressão e implantações em ambientes de Desenvolvimento (DEV) e Homologação (STAGING) operam de forma autônoma (Tiers T1–T3). A promoção final para **Produção (PROD)** exige validação humana obrigatória (*Human-in-the-Loop*) ou aprovação por *Quality Gate* determinístico estrito.

---

## 2. Arquitetura em 3 Planos, Resiliência e Matriz de Roteamento de Modelos

### 2.1 Matriz de Roteamento de Modelos Heterogêneos (Model Routing)

| Papel do Agente | Modelo / Provedor Recomendado | Justificativa Técnica e Função |
| :--- | :--- | :--- |
| **DiagnosticWorker** | *Fast/Cost-Effective* (GPT-4o-mini / Claude 3.5 Haiku) | Parsing rápido de stack trace, isolamento de arquivos e extração sanitizada de logs de erro. |
| **DevFixWorker (LLM A)** | *Frontier Reasoning* (Claude 3.5 Sonnet / GPT-4o) | Raciocínio profundo e alta capacidade de codificação para analisar a causa raiz e gerar o patch de correção (*diff*). |
| **BusinessDomainWorker (LLM C)** | *Domain Specialist / Contract Engine* (GPT-4o / Claude 3.5 Sonnet + Vetorizador) | Dual-mode: Validação de contratos de API/Schema e RAG sobre BDD/OpenAPI. Garante que o patch respeite o negócio. |
| **QARegressionWorker (LLM B)** | *Adversarial Coding* (DeepSeek Coder v2 / Qwen 2.5 Coder / GPT-4o-mini) | Modelo diferente de LLM A atuando como *Checker*. Escreve suítes de testes de unidade/integração para provar a eficácia. |
| **DeploymentPromotionWorker** | *Deterministic Pipeline / Python CLI* | Execução sem LLM. Interage com Git (Worktrees), motores de CI/CD e gera a requisição de aprovação em Produção. |

### 2.2 As 6 Travas Práticas de Resiliência de CI/CD

```text
[Falha no CI/CD]
       │
       ▼
1. PORTÃO ANTI-FLAKY ──► Re-executa testes 1-2x. Se passar ──► [ABORTA - Flaky Test Detectado]
       │ (Confirmado Bug Real)
       ▼
2. TRIAGEM FAST-PATH ──► Nível 0: Tenta linter/formatador determinístico (autopep8, prettier)
       │ (Falha Semântica Profunda)
       ▼
3. TAINT CONTROL     ──► Sanitiza logs de erro (remove PII, segredos e tags de Prompt Injection)
       │
       ▼
4. GIT WORKTREE      ──► Isola execução em branch efémera `fix/auto-healing-{job_id}`
       │
       ▼
5. ERROR STATE HASH  ──► Valida MD5(Stack Trace + Failed Tests). Se repetir hash ──► [ABORTA - Loop Detectado]
       │
       ▼
6. ROLLING COMMENT   ──► Atualiza 1 único comentário dinâmico no PR do GitHub/GitLab
```

1. **Portão de Reprodutibilidade (Anti-Flaky Test Gate):** Re-executa os testes falhos de forma isolada antes de invocar os agentes de IA. Se o teste passar na re-tentativa, a falha é classificada como instabilidade de infraestrutura e o processo é abortado sem consumo de tokens.
2. **Triagem Econômica em Cascatas (Fast-Pathing / FinOps):**
   * *Nível 0 (Determinístico):* Tenta correção via formatadores e linters estáticos (`autopep8`, `prettier`, `eslint --fix`).
   * *Nível 1 (Modelo Leve):* Correção de sintaxe e imports simples via modelos econômicos.
   * *Nível 2 (Comitê Completo):* Aciona o fluxo heterogêneo com LLM A, LLM C e LLM B apenas para falhas semânticas complexas.
3. **Controle de Contaminação e Injeção Indireta (Taint Control):** Filtro pré-prompt que limpa dados externos contidos nos logs de erro (requisições HTTP, payloads), removendo padrões de *Indirect Prompt Injection* e mascarando credenciais.
4. **Higiene de Git & Isolamento por Worktree:** Operação em `git worktree` dedicado. Antes de aplicar qualquer commit, valida se o `HEAD_COMMIT` do branch de origem permanece inalterado para evitar conflito com *pushes* de desenvolvedores humanos.
5. **Detecção de Loops Semânticos por Hash de Estado (Error State Hashing):** O orquestrador calcula o hash MD5/SHA256 da pilha de erro e dos testes falhos a cada iteração. Se o mesmo hash for detectado em duas iterações da mesma sessão, o laço de oscilação ($Bug A \leftrightarrow Bug B$) é interrompido imediatamente.
6. **Notificação de Comentário Único (*Rolling Single-Comment*):** O orquestrador mantém um único comentário ativo no Pull Request, realizando edições *in-place* via API do GitHub/GitLab para atualizar o progresso do diagnóstico e validação sem gerar poluição visual.

---

## 3. Design Detalhado do Sub-Agente de Negócio Dual-Mode (`BusinessDomainWorker`)

### 3.1 Modo 1: Garantia de Integridade de Contratos (Universal - Sem Docs)
Quando o repositório não possui documentação formal de requisitos ou BDD, o `BusinessDomainWorker` opera no **Modo Universal de Preservação de Contrato**:
* **Breaking Change Analysis:** Analisa o *diff* para garantir que assinaturas de métodos públicos, contratos REST/gRPC (schemas JSON, OpenAPI) e tipos de retorno não foram alterados de forma incompatível.
* **Preservação de Suítes Existentes:** Garante que nenhum teste antigo tenha sido removido, desativado (`@Disabled`, `@pytest.mark.skip`) ou alterado em suas asserções para forçar o passe do build.
* **Mapeamento de Impacto Cruzado:** Verifica se as funções alteradas afetam outros módulos dependentes no AST (*Abstract Syntax Tree*) do projeto.

### 3.2 Modo 2: RAG de Domínio Funcional (Avançado - Com Docs/BDD)
Quando o repositório contém diretórios de documentação (`docs/`, `spec/`, BDD `.feature`, OpenAPI/Swagger):
* **Busca Vetorial & Injeção de Contexto:** Executa busca RAG recuperando as regras de negócio associadas aos módulos afetados.
* **Validação de Requisitos:** Avalia se o patch do `DevFixWorker` respeita as regras de domínio declaradas (ex.: limites financeiros, fluxos de estado, permissões de usuário).
* **Parecer de Negócio:** Emite o contrato `BusinessDomainCheckContract` aprovando ou rejeitando o patch com justificativa técnica e orientação de refatoração.

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

### 4.2 Parecer de Negócio Dual-Mode (`BusinessDomainCheckContract`)
```json
{
  "title": "BusinessDomainCheckContract",
  "type": "object",
  "properties": {
    "check_id": { "type": "string" },
    "mode_executed": { 
      "type": "string", 
      "enum": ["UNIVERSAL_CONTRACT_INTEGRITY", "ADVANCED_BUSINESS_RAG"] 
    },
    "approved": { "type": "boolean" },
    "contract_violations": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "type": { "type": "string", "enum": ["BREAKING_API_CHANGE", "TEST_SUPPRESSION", "DOMAIN_RULE_BREACH", "CROSS_MODULE_IMPACT"] },
          "rule_or_file": { "type": "string" },
          "description": { "type": "string" },
          "severity": { "type": "string", "enum": ["WARNING", "BLOCKING"] }
        },
        "required": ["type", "description", "severity"]
      }
    },
    "guidance_for_dev_agent": { "type": "string" }
  },
  "required": ["check_id", "mode_executed", "approved"]
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
    "resilience_metrics": {
      "type": "object",
      "properties": {
        "flaky_check_passed": { "type": "boolean" },
        "fast_path_level": { "type": "integer" },
        "error_state_hash": { "type": "string" },
        "attempts_count": { "type": "integer" }
      },
      "required": ["flaky_check_passed", "fast_path_level", "error_state_hash"]
    },
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
        "mode_executed": { "type": "string" },
        "approved": { "type": "boolean" },
        "checked_specifications": { "type": "array", "items": { "type": "string" } }
      },
      "required": ["mode_executed", "approved"]
    },
    "patch_applied": {
      "type": "object",
      "properties": {
        "worktree_branch": { "type": "string" },
        "diff_content": { "type": "string" }
      },
      "required": ["worktree_branch", "diff_content"]
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
  "required": ["healing_id", "pipeline_id", "resilience_metrics", "root_cause_analysis", "business_domain_approval", "patch_applied", "regression_tests", "promotion_status"]
}
```

---

## 5. Diagrama de Sequência e Fluxo Operacional Completo

```text
[CI/CD Event] ──► Webhook Trigger
                        │
                        ▼
      [Anti-Flaky Test Gate & Fast-Pathing]
                        │ (Confirmed Real Bug - Fast Path Level 2)
                        ▼
             [Taint Control Filter]
            (Sanitizes error logs)
                        │
                        ▼
             [SelfHealingOrchestrator]
            (Spawns Git Worktree branch)
                        │
      ┌─────────────────┼─────────────────┬─────────────────┐
      │ (1. Diagnose)   │ (2. Technical)  │ (3. Business)   │ (4. Test)
      ▼                 ▼                 ▼                 ▼
[DiagnosticWorker]  [DevFixWorker]   [BusinessDomain]  [QARegression]
 (GPT-4o-mini)      (LLM A: Sonnet)  (LLM C: Dual-Mode)(LLM B: DeepSeek)
      │                 │                 │                 │
      └─ Stack Trace ──►├─ Code Patch ───►│                 │
                        │                 ├─ Contract/RAG ──┤
                        │                 │  Check          │
                        │                 │  (If Violates:  │
                        │                 │   Rejects Patch)│
                        │                 │                 ├─ Write Tests ──► [Quality Gate]
                        │                 │                 │                  (Sandbox Container)
                        │                 │                 │                         │
                        │                 │                 │                         ├── IF PASSED
                        │                 │                 │                         ▼
                                                                             [DeploymentPromotionWorker]
                                                                                      │
                                                                       ┌──────────────┴──────────────┐
                                                                       ▼                             ▼
                                                                 Deploy Staging                 Request PROD
                                                                   (Tier T3)                   (Tier T2 - HITL)
                                                                       │                             │
                                                                       └──────────────┬──────────────┘
                                                                                      ▼
                                                                           [Rolling Single Comment]
                                                                           (In-place PR Update)
```

---

## 6. Governança, Autonomia Graduada e Segurança

### 6.1 Matriz de Autonomia Graduada

| Nível (*Tier*) | Escopo de Ação Permitido | Regras de Governança e Portões de Segurança |
| :--- | :--- | :--- |
| **Tier T1 (Probatório)** | Leitura de logs e análise de causa raiz | Leitura estrita de logs de erro sanitizados e código. Nenhuma alteração permitida. |
| **Tier T2 (Supervisionado)** | Promoção para **PRODUÇÃO** | Exige **aprovação humana obrigatória (*Human-in-the-Loop*)** ou *Quality Gate* determinístico estrito. |
| **Tier T3 (Confiável)** | Patch, Testes e Deploy em **DEV / STAGING** | Execução autônoma de criação de branch worktree, fix, validação de regras de negócio, testes e deploy em Staging. |
| **Tier T4 (Autônomo)** | Autocorreção de infraestrutura leve em Staging | Restrito a ambientes de não-produção com auditoria assíncrona pós-execução. |

### 6.2 Travas Operacionais e Limites de Runtime
* `MAX_PASSOS`: 5 iterações ReAct por sub-agente.
* `MAX_HEALING_ATTEMPTS`: Máximo de 2 tentativas de correção por evento.
* `ERROR_STATE_HASHING`: Interrupção imediata se o hash MD5 de erro se repetir.
* `TOKEN_BUDGET_PER_HEALING`: 35.000 tokens distribuídos entre os modelos.
* `SANDBOX_ISOLATION`: Compilação e testes rodando em containers efémeros sem acesso à rede externa.

---

## 7. Roteiro de Implementação

* **Fase 1 (Resiliência, Core ReAct & Diagnostic Worker):** Implementar o Portão Anti-Flaky, Taint Control para sanitização de logs e o laço ReAct base.
* **Fase 2 (DevFix, Business Domain Dual-Mode & QA Workers):** Desenvolver o Agente Desenvolvedor (LLM A), o Guardião de Regras de Negócio Dual-Mode (Contratos + RAG) e o Agente de QA Adversarial (LLM B).
* **Fase 3 (Worktrees, Gatilho por Evento & Staging Deploy):** Integrar a gestão de Git Worktrees, webhook de gatilho do CI/CD, Rolling Single-Comment e automação de deploy em Staging (Tier T3).
* **Fase 4 (Governança PROD & Control Plane):** Adicionar a validação de política *Deny-by-Default* (PEP), a interface de aprovação humana (*Human-in-the-Loop*) para Produção (Tier T2) e o registro imutável na Trilha de Auditoria.
