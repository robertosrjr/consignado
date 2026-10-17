# Business Fingerprint v2 — implementação

## Responsabilidades

- Duplicidade técnica: mesmo parceiro e `Idempotency-Key`; preserva a operação original. Conteúdo diferente retorna 422.
- Equivalência semântica: chave técnica nova, mas mesma intenção normalizada; HTTP 202, `X-Idempotency-Status: DUPLICATE_REJECTED` e rastreamento da proposta original.
- Concorrência comercial: propostas diferentes podem disputar a mesma margem. Esta mudança não implementa reserva de margem, integração Dataprev/eSocial ou máquina de reconciliação.

A implementação segue `rules/instrucao-ia-implementacao-business-fingerprint-v2.md`, adaptada à arquitetura hexagonal e ao Java 25/Spring Boot 4 já existentes no projeto. Não altera versões globais nem introduz os pacotes ilustrativos `com.bank.*`.

## Canonicalização

Base: `CPF|CONVENIO|VALOR_COM_2_CASAS|PARCELAS|DATA_ISO_UTC`.

CPF passa pelo value object existente: preserva zeros e valida dígitos verificadores. Convênio aceita trim e caixa indiferente na entrada HTTP. Quantias equivalentes como `15000`, `15000.0` e `15000.000` produzem `15000.00`. O normalizador usa HALF_EVEN, mas a validação financeira existente continua rejeitando valores efetivamente fracionários além dos centavos, em vez de alterar dinheiro silenciosamente.

O HMAC inclui ainda domínio/versionamento do algoritmo, parceiro autenticado e portabilidade. Sem esses campos, propostas de parceiros diferentes ou de modalidades diferentes seriam indevidamente agrupadas. O segredo nunca tem fallback. Canonical string, HMAC e CPF não são adicionados a logs, labels ou respostas. HMAC é pseudonimização, não anonimização nem comprovação isolada de conformidade LGPD.

`contractDate` é um campo opcional novo. Quando ausente, a equivalência semântica vale para a data UTC do recebimento. Em retries técnicos, a ausência é representada de forma estável, permitindo recuperar a operação original mesmo cruzando meia-noite. Se fornecido, o contrato usa a data informada, não a data da máquina. Um novo UUID no dia seguinte pode representar nova intenção quando a data foi omitida; para estabilidade entre dias, envie uma data contratual explícita.

## Persistência atômica e compatibilidade do pipeline

Em uma única transação DynamoDB são incluídos:

1. Alias técnico na tabela nova: `PK=HEADER#<tenant>#<Idempotency-Key>`, `SK=METADATA`.
2. Claim semântico: `PK=HASH#<versao>:<HMAC>`, `SK=METADATA`.
3. Proposta na tabela de inbox existente, usando um UUID independente como `idempotencyKey` interno.

Cada inclusão exige ausência da chave. A tabela nova não tem Streams: somente o item da inbox segue para dispatcher, SQS e worker. O esquema e as mensagens existentes continuam aceitando UUIDs. O UUID interno é devolvido como `proposalId`; o campo público `idempotencyKey` continua ecoando a chave do cliente.

Em duplicata semântica, o novo alias técnico é gravado transacionalmente com uma ConditionCheck da identidade do claim original. Isso impede que a nova chave seja depois reaproveitada para outro payload. Leituras de resolução são fortemente consistentes. Um conflito sem vencedor confirmado retorna 503; throttling ou erro do SDK nunca são classificados por si só como duplicidade. Repita com a mesma chave técnica.

Metadata armazena identificadores, HMAC de comparação, tenant, data de negócio normalizada, timestamp e TTL. A data de negócio também fica na inbox para preservar o contexto contratual usado na deduplicação. O payload da inbox continua protegido pelo mecanismo de criptografia existente. Não há reserva isolada de fingerprint antes da proposta durável.

O cache/coalescer antigo é usado somente quando a funcionalidade está desligada. Ele não compara payload; portanto não envolve o novo adaptador. Isso tem custo de desempenho a medir antes de ampliar carga.

## Contrato HTTP

| Caso | HTTP | Header X-Idempotency-Status | Body status |
|---|---|---|---|
| Nova proposta | 202 | NEW | RECEIVED |
| Retry técnico | 202 | DUPLICATE_ACCEPTED | DUPLICATE_ACCEPTED |
| Mesmo negócio, UUID novo | 202 | DUPLICATE_REJECTED | DUPLICATE_ACCEPTED |
| Mesma chave, conteúdo diferente | 422 | — | Problem Details |
| Armazenamento ou KMS indisponível | 503 | — | Problem Details |

O status de body anterior foi preservado para consumidores existentes; o header distingue a nova classificação. Duplicidade semântica não significa proposta negada por regra comercial. `proposalId`, `tracingId` e `receivedAt` em duplicatas identificam a proposta original. `X-Tenant-ID`, JWT e `Idempotency-Key` continuam obrigatórios.

## Configuração e infraestrutura

Desligado por padrão, pois requer tabela e chave novas. Não foi aplicado em AWS por esta implementação.

```text
INGESTION_FINGERPRINT_ENABLED=true
INGESTION_FINGERPRINT_MODE=kms
INGESTION_FINGERPRINT_TABLE=ProposalDeduplication
INGESTION_FINGERPRINT_KEY_VERSION=v2
INGESTION_FINGERPRINT_KMS_KEY_ID=<ARN imutável de chave HMAC_256>
```

KMS usa `GenerateMac` com HMAC_SHA_256. Uma chave de criptografia AES existente não serve como chave HMAC. A role precisa de `kms:GenerateMac` na chave e de `GetItem`/`PutItem` nas duas tabelas, além de `ConditionCheckItem` na tabela de deduplicação. Autorizações das transações são verificadas pelas ações individuais. O módulo `infrastructure/modules/business-fingerprint` fornece tabela PK/SK com TTL, PITR, proteção de exclusão, chave HMAC e política para uma role existente. Deve ser integrado a um ambiente e ter seu plano revisado antes de aplicação; não altera a inbox existente.

O modo `local` exige `INGESTION_INBOX_TYPE=memory` e `INGESTION_FINGERPRINT_LOCAL_KEY` Base64 com pelo menos 32 bytes, fornecida explicitamente. Esse modo não é durável e serve somente para desenvolvimento/testes. A chave deve ser gerada fora do código; não reutilize a chave AES como segredo HMAC.

O signer KMS faz duas chamadas por requisição (comparação técnica e semântica), inclusive retries. Avaliar cotas, latência e custo sob a carga real. Não existe benchmark novo que comprove 10 mil req/s com essa configuração.

TTL segue a retenção da inbox, atualmente P7D. A implementação é conservadora: um claim expirado continua bloqueando até sua exclusão física. Não faz takeover baseado apenas no relógio. A expiração do TTL não é prova de conclusão; retenção precisa cobrir backlog, replay de DLQ e janela comercial. Não ativar em cenários onde propostas possam continuar pendentes após essa retenção sem definir proteção durável adicional.

## Migração e rollback

1. Provisionar recursos e permissões; manter a flag desligada até estarem disponíveis.
2. Testar em ambiente isolado com o mesmo contrato e pipeline.
3. Definir um corte de tráfego e drenar/reconciliar propostas antigas. Registros antigos não têm fingerprint: não é possível deduplicar semanticamente novos UUIDs contra eles sem backfill seguro e acesso ao payload protegido.
4. Uma chave técnica antiga ainda presente na inbox é rejeitada com 422 quando o novo modo está ativo, pois seu conteúdo não pode ser comparado com segurança pelo novo índice. Não há replay transparente desses registros; combinar a transição com parceiros.
5. Ativar de forma coordenada em todas as instâncias. Não misturar réplicas com e sem a flag: as identidades gravadas diferem. Não desligar após aceitar novos negócios sem drenar e preparar migração reversa; o modo antigo não consulta os novos aliases.
6. Não trocar chave KMS nem key-version durante a janela ativa de deduplicação. Mudar o HMAC cria outro namespace e pode readmitir intenções antigas. Rotação exige estratégia de consulta a versões anteriores ou corte/drenagem; não está automatizada.
7. Fazer backup/restauração coordenados das duas tabelas. Restaurar apenas uma pode deixar referências inconsistentes; reconciliação é necessária antes de liberar tráfego.

## Observabilidade e limites

Novos contadores: `ingestion_deduplicated_total{kind="technical|semantic"}` e `ingestion_idempotency_mismatch_total`. Duplicatas não aumentam os contadores de VGV recebido. Não há labels por CPF, chave técnica, fingerprint ou proposalId.

Esta camada impede novas inclusões equivalentes durante a janela de retenção, mas não torna o processamento do worker exactly-once. Persistência terminal do worker, exclusão mútua entre réplicas, outbox de eventos e contrato idempotente durável com o legado continuam sendo evoluções separadas, identificadas em `auditoria-idempotencia.md`.

## Validação

Os testes cobrem normalização, isolamento de segredo/tenant/portabilidade, UTC, header técnico, alias semântico, divergência 422, concorrência HTTP, estrutura de transação e falhas do SDK. A integração com DynamoDB Local verifica a transação e concorrência em armazenamento real, não apenas em mock. A distinção comercial é testada como limite: intenções diferentes passam pela ingestão; reserva de margem não foi implementada nem declarada validada.

Comandos no diretório `applications`:

```powershell
mvn -o -pl ingestion-api -am spotless:apply test
mvn -o -P integration -pl ingestion-api -am verify
```

## Referências

- [Transações DynamoDB](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transaction-apis.html)
- [IAM das transações](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transaction-apis-iam.html)
- [HMAC no KMS](https://docs.aws.amazon.com/kms/latest/developerguide/hmac.html)
- [TTL DynamoDB](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/TTL.html)

## Evidências de execução registradas em 16/10/2026

Os comandos acima são meios de reproduzir a validação, não uma afirmação de que toda a suíte passou. Os testes focados de Business Fingerprint e a integração específica do novo adaptador com DynamoDB Local passaram. A verificação ampla de integração encontrou um timeout no teste concorrente existente de `DynamoDbInboxAdapterIT`. A causa deve ser reavaliada antes de declarar o build completo aprovado. Não foi executada uma nova suíte nesta revisão documental.
