# Guia de Referência Técnica: Padrão de Idempotência em APIs e Sistemas Distribuídos

## 1. Definição e Conceitos Fundamentais

A **Idempotência** é a propriedade pela qual uma operação pode ser executada múltiplas vezes sem alterar o resultado final além da aplicação inicial. Em sistemas distribuídos onde falhas de rede, timeouts e retentativas automáticas (*retries*) são inevitáveis, a idempotência garante a semântica de processamento **Exactly-Once** no nível de aplicação.

$$\text{Operação}(x) = \text{Operação}(\text{Operação}(x))$$

---

## 2. Matriz de Idempotência dos Métodos HTTP (RFC 9110)

| Método HTTP | Idempotente? | Seguro (Read-Only)? | Descrição & Comportamento |
|---|---|---|---|
| **GET** | ✅ Sim | ✅ Sim | Apenas recupera dados. Chamadas repetidas retornam o mesmo recurso sem efeitos colaterais. |
| **HEAD / OPTIONS** | ✅ Sim | ✅ Sim | Recupera metadados/cabeçalhos. Sem modificação de estado. |
| **PUT** | ✅ Sim | ❌ Não | Substituição completa do recurso. Múltiplas chamadas idênticas resultam no mesmo estado final no banco. |
| **DELETE** | ✅ Sim | ❌ Não | Remoção de recurso. A primeira chamada remove o recurso; chamadas subsequentes retornam `404` ou `204`, mas o estado final (recurso ausente) é idêntico. |
| **POST** | ❌ Não (por padrão) | ❌ Não | Criação de novos recursos. Requer o uso de **Idempotency Keys** para tornar-se seguro contra retentativas. |
| **PATCH** | ⚠️ Depende | ❌ Não | Atualização parcial. É idempotente se aplicar mutações absolutas (`set status = ACTIVE`), mas **não** é idempotente para incrementos (`increment count + 1`). |

---

## 3. Arquitetura da Chave de Idempotência (Idempotency Key Pattern)

### Fluxo de Sequência e Estados

```text
Cliente                        Servidor API                     Cache/DB (Idempotency Store)
   |                                 |                                      |
   |--- POST /payments ------------->|                                      |
   |    Header: Idempotency-Key: X   |                                      |
   |                                 |--- Tenta adquirir Lock / Inserir --->|
   |                                 |    Key: X, Status: IN_PROGRESS       |
   |                                 |<-- Lock adquirido com sucesso -------|
   |                                 |                                      |
   |                                 | [ Executa Processamento ]            |
   |                                 | [ Debita Conta / Processa Cartão ]   |
   |                                 |                                      |
   |                                 |--- Atualiza Resposta Final --------->|
   |                                 |    Status: COMPLETED, Payload, Code  |
   |<-- Resposta 201 Created --------|                                      |
   |                                 |                                      |
   |==================== RETENTATIVA EM CASO DE TIMEOUT ====================|
   |                                 |                                      |
   |--- POST /payments (RETRY) ----->|                                      |
   |    Header: Idempotency-Key: X   |                                      |
   |                                 |--- Consulta Estado da Chave X ------>|
   |                                 |<-- Retorna: Status COMPLETED + Body -|
   |<-- Resposta 201 Created --------|                                      |
   |    (Retorna resposta em cache sem reexecutar o débito)                 |
```

---

## 4. Schema de Banco de Dados para Controle de Idempotência

### Tabela SQL (PostgreSQL / MySQL)

```sql
CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    client_id VARCHAR(100) NOT NULL,
    request_path VARCHAR(255) NOT NULL,
    request_hash CHAR(64) NOT NULL, -- Hash SHA-256 do payload para validação de mismatch
    status VARCHAR(20) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
    response_code INT NULL,
    response_body JSONB NULL,
    response_headers JSONB NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    locked_until TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Índices para busca rápida e expiração
CREATE INDEX idx_idempotency_lookup ON idempotency_keys(idempotency_key, client_id);
CREATE INDEX idx_idempotency_expiration ON idempotency_keys(expires_at);
```

---

## 5. Exemplo de Middleware de Idempotência (TypeScript / Express / Redis)

```typescript
import { Request, Response, NextFunction } from 'express';
import crypto from 'crypto';
import Redis from 'ioredis';

const redis = new Redis(process.env.REDIS_URL);
const LOCK_TTL_SECONDS = 30; // Trava contra requisições concorrentes simultâneas
const KEY_EXPIRATION_SECONDS = 86400; // 24 horas de retenção de resposta

export async function idempotencyMiddleware(req: Request, res: Response, next: NextFunction) {
  const idempotencyKey = req.header('Idempotency-Key');

  // Ignora chamadas sem chave em métodos que não exigem obrigatoriedade
  if (!idempotencyKey) {
    return next();
  }

  const clientId = req.user?.id || 'anonymous';
  const redisKey = `idempotency:${clientId}:${idempotencyKey}`;
  const lockKey = `lock:${redisKey}`;

  // Calcula o hash do payload para verificar se os parâmetros bateram
  const currentPayloadHash = crypto
    .createHash('sha256')
    .update(req.originalUrl + JSON.stringify(req.body || {}))
    .digest('hex');

  // Tenta adquirir trava concorrente
  const acquiredLock = await redis.set(lockKey, 'LOCKED', 'NX', 'EX', LOCK_TTL_SECONDS);
  if (!acquiredLock) {
    return res.status(409).json({
      error: 'concurrent_request',
      message: 'Uma requisição com esta mesma chave de idempotência está em processamento ativo.'
    });
  }

  try {
    const cachedData = await redis.get(redisKey);

    if (cachedData) {
      const record = JSON.parse(cachedData);

      // Validação de Mismatch: a mesma chave não pode ser reutilizada com payload diferente
      if (record.payloadHash !== currentPayloadHash) {
        await redis.del(lockKey);
        return res.status(422).json({
          error: 'idempotency_mismatch',
          message: 'A chave de idempotência foi reutilizada com um payload ou endpoint diferente.'
        });
      }

      // Se a requisição anterior foi concluída, retorna a resposta salva diretamente do cache
      await redis.del(lockKey);
      return res.status(record.statusCode).set(record.headers).json(record.body);
    }

    // Intercepta a resposta da API para salvar no Redis ao finalizar
    const originalJson = res.json.bind(res);
    res.json = (body: any) => {
      if (res.statusCode >= 200 && res.statusCode < 500) {
        const cachePayload = {
          statusCode: res.statusCode,
          headers: { 'Content-Type': 'application/json' },
          body: body,
          payloadHash: currentPayloadHash
        };
        redis.set(redisKey, JSON.stringify(cachePayload), 'EX', KEY_EXPIRATION_SECONDS);
      }
      redis.del(lockKey);
      return originalJson(body);
    };

    next();
  } catch (error) {
    await redis.del(lockKey);
    next(error);
  }
}
```

---

## 6. Edge Cases e Soluções Arquiteturais

1. **Requisicões Concorrentes Simultâneas**:
   - *Risco*: Duas chamadas com a mesma chave chegam no mesmo milissegundo.
   - *Solução*: Utilizar trava atômica distribuída (`SET NX` no Redis ou constraint `UNIQUE` no SQL) antes de iniciar a transação.
2. **Queda do Servidor Durante o Processamento**:
   - *Risco*: O servidor cai enquanto processava e a chave fica travada em `IN_PROGRESS` sem resposta salva.
   - *Solução*: O lock possui tempo de expiração curto (ex.: 30 segundos). Após o tempo limite, retentativas são autorizadas a reprocessar ou checar o estado nos logs de auditoria.
3. **Erros 500 (Internal Server Error)**:
   - *Risco*: O banco falhou temporariamente e salvou um status 500 no cache, bloqueando chamadas legítimas subsequentes.
   - *Solução*: Salvar apenas resultados bem-sucedidos (`2xx`) ou erros de negócios do cliente (`4xx`). Erros `5xx` de infraestrutura não devem ser cacheados como resposta final de idempotência.
