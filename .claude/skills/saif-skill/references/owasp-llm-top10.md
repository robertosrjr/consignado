# Reference: OWASP Top 10 for LLM Applications (2025 v2.0) × SAIF

Severidade usada nos gates de CI e o risco ou controle SAIF correspondente (ver `saif-references.md`).

| ID | Vulnerabilidade | Descrição no Pipeline | Severidade | Correspondente SAIF |
|---|---|---|---|---|
| **LLM01:2025** | Prompt Injection | Injeção direta/indireta para burlar instruções ou executar comandos não autorizados. | CRITICAL | Risco *Prompt Injection*; controle *Input/Output Validation & Sanitization* |
| **LLM02:2025** | Sensitive Info Disclosure | Exposição indevida de PII, chaves ou dados confidenciais na saída do modelo/logs. | CRITICAL | Risco *Sensitive Data Disclosure*; controle *Output Sanitization* |
| **LLM05:2025** | Improper Output Handling | Saída de LLM executada sem validação ou sanitização estruturada. | MAJOR | Controle *Output Validation & Sanitization*; risco *Insecure Integrated Component* |
| **LLM06:2025** | Excessive Agency | Permissão excessiva concedida a agentes autônomos para executar ferramentas/APIs. | CRITICAL | Risco *Rogue Actions*; controles *Agent Permissions* e *Agent User Control* |
| **LLM07:2025** | System Prompt Leakage | Vazamento de instruções internas ou segredos do sistema através de prompts. | MAJOR | Risco *Sensitive Data Disclosure* (inclui system prompts) |
