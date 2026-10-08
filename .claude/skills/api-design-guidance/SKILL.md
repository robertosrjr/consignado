---
name: api-design-guidance
description: "Use this skill when designing REST APIs, defining response formats, documenting endpoints with OpenAPI, or establishing API conventions."
---

# Convenções de API: Design System & Documentação

Você é um especialista em **design de APIs REST**, **padrões de resposta**, **documentação OpenAPI/Swagger** e **versionamento**. Sua missão é garantir consistência e qualidade nas APIs do projeto.

## Quando Usar Esta Skill

- Desenhar novos endpoints REST
- Definir padrões de resposta e erro
- Documentar APIs com OpenAPI/Swagger
- Revisar consistência de contratos
- Estabelecer estratégia de versionamento
- Validar status HTTP corretos
- Definir convenções de paginação

## Padrões de API

### 1. Convenção de Nomes e Recursos

✅ **Nomes no plural, verbos HTTP semânticos**
```
GET    /api/v1/orders              → listar pedidos
POST   /api/v1/orders              → criar pedido
GET    /api/v1/orders/{id}         → obter um pedido
PUT    /api/v1/orders/{id}         → substituir pedido (idempotente)
PATCH  /api/v1/orders/{id}         → atualizar parcialmente
DELETE /api/v1/orders/{id}         → deletar pedido
```

❌ **Antipadrões**
```
GET  /api/getOrder             → verbo no nome
POST /api/orders/create        → verbo redundante
GET  /api/order/1              → singular
```

### 2. Status HTTP Corretos

| Código | Significado | Quando usar |
|--------|-------------|------------|
| `200` | OK | Sucesso com corpo na resposta |
| `201` | Created | Criação bem-sucedida (POST) |
| `204` | No Content | Sucesso, sem corpo (PUT, DELETE) |
| `400` | Bad Request | Erro de validação de entrada |
| `404` | Not Found | Recurso não encontrado |
| `409` | Conflict | Conflito (ex: violação de constraint) |
| `422` | Unprocessable Entity | Regra de negócio violada |
| `500` | Internal Server Error | Erro não tratado |

### 3. Formato de Resposta Padrão

**Sucesso com um recurso (200, 201):** o recurso vai direto no corpo, sem envelope.
```json
GET /api/v1/orders/{id}

{ "id": "7d36ce98-...", "status": "PENDING", "total": { "amount": 110.00, "currency": "BRL" } }
```

**Sucesso com uma coleção:** sempre paginada e envelopada em `{data, pagination}` (ver seção 4). Nunca devolva um array solto: ele não comporta metadados sem quebrar o contrato.

**Erro (RFC 7807 — Problem Details):**
```json
{
  "type": "https://api.exemplo.com/errors/validation-error",
  "title": "Validation Error",
  "status": 422,
  "detail": "Campo 'email' é obrigatório",
  "instance": "/api/v1/orders",
  "errors": [
    {
      "field": "email",
      "message": "Email inválido"
    }
  ]
}
```

Use `ProblemDetail` nativo do Spring Boot 3.x:

```java
@ExceptionHandler(ValidationException.class)
public ProblemDetail handleValidation(ValidationException ex) {
    ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
    detail.setTitle("Validation Error");
    detail.setDetail(ex.getMessage());
    return detail;
}
```

### 4. Paginação Padronizada

Regras:
- `page` começa em **1**; `size` padrão 20, máximo 100.
- Ordem **estável** (ex.: mais recente primeiro, com o ID como desempate); sem ela, páginas repetem ou pulam itens.
- Parâmetro fora dos limites responde **400**, nunca 500: valide na borda (`@Min`/`@Max`, que também documentam o OpenAPI) e no objeto de paginação da camada de aplicação.
- A paginação é um conceito da camada de aplicação: a porta de saída recebe e devolve tipos próprios, sem `org.springframework.data`.

```json
GET /api/v1/orders?page=1&size=20

{
  "data": [ /* items */ ],
  "pagination": {
    "page": 1,
    "size": 20,
    "totalItems": 150,
    "totalPages": 8,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

Ou com **cursor** (melhor para grandes datasets):
```json
GET /api/v1/orders?cursor=abc123&limit=20

{
  "data": [ /* items */ ],
  "pagination": {
    "nextCursor": "xyz789",
    "prevCursor": "def456",
    "hasNext": true
  }
}
```

### 5. Versionamento

**Via path (recomendado):**
```
GET /api/v1/orders
GET /api/v2/orders  → mudanças breaking
```

**Via header:**
```
Accept: application/vnd.exemplo.v1+json
```

**Regra**: Mudanças breaking exigem nova versão. Nunca altere um endpoint silenciosamente.

## Documentação com OpenAPI/Swagger

### Dependência Maven
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.x.x</version>
</dependency>
```

Para stack reativa, use `springdoc-openapi-starter-webflux-ui`.

### Regras de Documentação
- Toda controller pública tem `@Tag`, `@Operation` e `@ApiResponse`.
- DTOs de request/response documentados com `@Schema(description = ...)`.
- A especificação é versionada junto com a API (mesma estratégia de versionamento).

### Anotações Essenciais
```java
@Tag(name = "Orders", description = "Gerenciamento de pedidos")
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    
    @Operation(
        summary = "Listar pedidos",
        description = "Retorna lista paginada de pedidos do usuário autenticado"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Lista de pedidos",
        content = @Content(schema = @Schema(implementation = OrderResponse.class))
    )
    @GetMapping
    public ResponseEntity<PagedResponse<OrderResponse>> list(
        @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        // ...
    }
}

@Schema(description = "Resposta de pedido")
public record OrderResponse(
    @Schema(description = "ID único do pedido") Long id,
    @Schema(description = "Status do pedido") String status,
    @Schema(description = "Data de criação") LocalDateTime createdAt
) {}
```

### URLs de Acesso
- **UI Swagger**: `/swagger-ui.html`
- **Spec JSON**: `/v3/api-docs`
- **Spec YAML**: `/v3/api-docs.yaml`

## Imutabilidade de Contrato

- Adicione campos opcionais sem quebra (forward compatibility)
- Nunca remova campos obrigatórios (breaking change)
- Nunca altere o significado/tipo de um campo (breaking change)
- Documente deprecações antes de remover

## Como Responder

1. **Análise**: Revisar design do endpoint/contrato
2. **Aplicação**: Sugerir ajustes conforme padrões
3. **Implementação**: Código com anotações e configuração
4. **Documentação**: OpenAPI/Swagger pronto
