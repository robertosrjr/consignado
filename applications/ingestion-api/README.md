# ingestion-api

A borda de absorção. Aceita a proposta com durabilidade e idempotência e responde `202 Accepted` **sem esperar o legado**.

## O problema que resolve

Na virada da folha, robôs de correspondentes disparam cerca de 10.000 propostas por segundo. O Core Banking aguenta 180 por segundo. Se a borda chamasse o legado de forma síncrona, ele cairia nos primeiros segundos e o cliente iria para o concorrente.

A borda então faz o mínimo necessário para **não perder a proposta** e devolve na hora. O trabalho pesado acontece depois, no ritmo que o legado suporta.

## Fluxo de uma requisição

```mermaid
sequenceDiagram
    participant R as Robô do parceiro
    participant I as ingestion-api
    participant D as DynamoDB (DurableInbox)
    R->>I: POST /v1/proposals (JWT, X-Tenant-ID, Idempotency-Key)
    I->>I: valida token, tenant e corpo
    I->>I: cota do parceiro (rate limit por tenant)
    I->>I: cifra o payload (AES-GCM + GZIP)
    I->>D: PutItem com attribute_not_exists(idempotencyKey)
    alt chave nova
        D-->>I: criado
        I-->>R: 202 { outcome: RECEIVED }
    else chave repetida
        D-->>I: condicional falhou (devolve o item antigo)
        I-->>R: 202 { outcome: DUPLICATE_ACCEPTED, mesmo tracingId }
    end
```

O p95 alvo é **menor que 100 ms**, e nada no caminho crítico chama o legado ou um banco relacional.

## Idempotência: por que a escrita condicional

A duplicata é descartada pelo próprio DynamoDB, com `attribute_not_exists(idempotencyKey)` em uma única operação atômica. Não há leitura antes da escrita, então não existe janela de corrida entre dois robôs enviando a mesma chave ao mesmo tempo. Isso é o que impede dupla averbação e, por consequência, pagamento em duplicidade.

Na falha condicional, o adaptador pede `ALL_OLD` e devolve o `tracingId` e o instante da **primeira** aceitação. O robô que repetiu recebe a mesma resposta da primeira vez, o que torna a repetição segura e barata.

## API

`POST /v1/proposals`

| Header | Obrigatório | Observação |
|---|---|---|
| `Authorization: Bearer <jwt>` | sim | validado por JWKS; a claim de tenant precisa bater com o header |
| `X-Tenant-ID` | sim | divergência do token resulta em `403` |
| `Idempotency-Key` | sim | UUID estrito |

| Status | Quando |
|---|---|
| `202` | aceita (`RECEIVED`) ou repetida (`DUPLICATE_ACCEPTED`) |
| `400` | corpo ou headers inválidos |
| `401` / `403` | token ausente ou tenant divergente |
| `413` | corpo acima do limite |
| `429` | cota do parceiro excedida (com `Retry-After`) |
| `503` | DurableInbox indisponível |

Todos os erros são `application/problem+json` (RFC 7807) sem detalhe interno. Nenhuma resposta devolve o CPF, e há teste garantindo isso.

## Configuração

Sem valor padrão (o serviço não sobe sem elas):

| Variável | Para quê |
|---|---|
| `INGESTION_JWT_JWK_SET_URI` | JWKS do emissor |
| `INGESTION_PAYLOAD_LOCAL_KEY` | chave AES-256 em Base64 (modo local) — a **mesma** do worker |

Principais opcionais: `INGESTION_INBOX_TYPE` (`dynamodb` ou `memory`), `INGESTION_INBOX_TABLE`, `INGESTION_QUOTA_DEFAULT_PER_SECOND`, `INGESTION_MAX_BODY_BYTES`, `INGESTION_PAYLOAD_MODE` (`local` ou `kms`), `INGESTION_API_DOCS_ENABLED`.

## Documentação interativa

Desligada por padrão, de propósito: uma implantação que esqueça a variável **não** expõe a documentação. O ambiente local liga com `INGESTION_API_DOCS_ENABLED=true` e serve em `/swagger-ui.html`.

## Como rodar

```bash
# com o ambiente local no ar (ver ../local-env)
mvn -pl ingestion-api spring-boot:run
```

Precisa de `mvn install -DskipTests` antes, para os módulos irmãos estarem no repositório local.

## Testes

```bash
mvn -pl ingestion-api test                  # 47 testes
mvn -pl ingestion-api verify -Pintegration  # + ITs contra DynamoDB Local embarcado
```

Os `*IT` só compilam no perfil `integration`, porque dependem do módulo `local-env`.

## Arquitetura

Hexagonal: `domain` (sem dependências) ← `application` (casos de uso e portas) ← `infrastructure` (adaptadores). Um teste ArchUnit falha o build se a direcao se inverter.
