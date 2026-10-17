---
name: idempotency-pattern-skill
description: Procedimento passo a passo para auditoria, projeto e implementação do Padrão de Idempotência e Chaves de Idempotência em APIs REST, filas de mensagens e banco de dados.
allowed-tools: Read, Grep, Glob, Bash
model: sonnet
---

# Skill: Implementação e Auditoria do Padrão de Idempotência

Esta Skill orienta a execução estruturada para auditar e implementar mecanismos de idempotência em serviços web e sistemas distribuídos, alinhado com as especificações da IETF, Stripe API e padrões de resiliência.

---

## Fluxo de Trabalho de Inspeção e Implementação

### Passo 1: Inspeção de Rotas Críticas e Headers
1. Identifique rotas mutáveis (`POST /payments`, `POST /orders`, `POST /transfers`, webhooks).
2. Verifique a obrigatoriedade da presença do cabeçalho `Idempotency-Key` (ou `X-Idempotency-Key`).
3. Confirme se a chave enviada possui entropia adequada (ex.: formato UUID v4).

### Passo 2: Verificação do Ciclo de Vida do Armazenamento (Store & Lock)
1. Para detalhes técnicos de schemas SQL, comandos Redis e diagramas de estado, consulte a referência em `references/idempotency-pattern-reference.md`.
2. Certifique-se de que a requisição realiza o Lock síncrono inicial:
   - Estado `IN_PROGRESS` registrado imediatamente no recebimento da requisição.
   - Rejeição de requisições concorrentes idênticas com status `409 Conflict` ou retenção controlada.
3. Verifique o salvamento da resposta final (status code, headers, body) com estado `COMPLETED`.

### Passo 3: Detecção de Divergência de Payload (Payload Mismatch)
1. Calcule o Hash (SHA-256) da combinação do caminho do endpoint + corpo da requisição JSON.
2. Armazene o hash junto com a chave de idempotência.
3. Se uma requisição subsequente com a mesma chave trouxer um hash diferente, retorne `422 Unprocessable Entity` ("Idempotency key payload mismatch").

### Passo 4: Auditoria de Expiração e Desempenho (TTL & Pruning)
1. Confirme a configuração de TTL (Time-To-Live) no Redis ou política de expiração no SQL (mínimo de 24 horas a 7 dias).
2. Valide que falhas internas do servidor (`500 Internal Server Error`) permitam a retentativa desmarcando a trava temporária ou permitindo o reprocessamento seguro.

---

## Output Esperado
Gere o parecer técnico listando as rotas auditadas, vulnerabilidades a duplicação detectadas e o blueprint de middleware corrigido.
