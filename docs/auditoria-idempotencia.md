# Auditoria de idempotência — Consignado

Data: 16/10/2026. Inspeção estática; não foram executados testes nem alteradas aplicações ou recursos AWS nesta auditoria.

Skill aplicada: `.claude/skills/idempotency-pattern-skill/idempotency-pattern-skill.md` e sua referência técnica.

Evolução posterior: a implementação opt-in de Business Fingerprint v2 está descrita em [business-fingerprint-v2-implementacao.md](business-fingerprint-v2-implementacao.md). Ela acrescenta comparação de conteúdo e escopo por parceiro na nova camada de aceitação. Os achados abaixo registram o caminho anterior, que permanece como padrão enquanto a flag está desligada; os pontos sobre persistência e concorrência do worker continuam pendentes.

## Escopo e diagnóstico

Rotas auditadas: `POST /v1/proposals` e o POST de averbação do mock legado, além do despacho e consumo assíncronos.

A aplicação já tem uma base útil: header obrigatório com validação de UUID, inclusão condicional atômica no DynamoDB, coalescência local de chamadas, reaproveitamento do identificador e instante originais, e envio da mesma chave ao legado. Isso não comprova proteção durável de todos os efeitos do processamento.

| Prioridade | Evidência no código | Risco e melhoria |
|---|---|---|
| Alta | `DynamoDbInboxAdapter` verifica existência, sem fingerprint; `CoalescingInboxAdapter` também não compara payload | Mesma chave com CPF, valor ou parcelas diferentes recebe sucesso de duplicata. Persistir fingerprint e comparar também no cache; responder 422 na divergência. |
| Alta | Cache usa tenant + chave; inbox persistida e repositório do worker usam somente chave | Parceiros podem colidir e a resposta de duplicata pode carregar metadados do registro de outro parceiro. Definir escopo uniforme em toda a cadeia; preferir tenant + chave, incluindo leitura, mensagens e contrato com legado. |
| Alta | `WorkerConfig` instancia `InMemoryProposalRepository` | Estado terminal se perde após reinício e não é compartilhado entre réplicas. Persistir estado e transições condicionais. |
| Alta | `ProcessProposalService` lê estado, grava envio, chama legado e só depois grava resultado | Duas execuções podem chamar o legado antes do estado terminal. Introduzir aquisição condicional de processamento com lease e proprietário; manter deduplicação durável no destino. |
| Média | `business.settled` é chamado após `repository.save`, que pode descartar gravação concorrente sem informar o chamador | Dois processamentos concorrentes podem contabilizar o mesmo negócio. Retornar resultado da transição e emitir evento uma vez por transição efetiva; para entrega durável, usar outbox e consumidor idempotente. |
| Média | `AverbacaoLedger` mantém mapa local limitado por tamanho | Deduplicação do mock se perde ao reiniciar ou expulsar chaves. Não usar como prova de deduplicação durável do legado real. |
| Média | Retenção configurada na ingestão: P7D; registro recebe TTL, condição verifica apenas existência | Confirmar TTL habilitado na tabela e definir política de reutilização. A exclusão física não acontece exatamente no instante de expiração. |
| Média | `writtenByThisRequest` usa tracingId para identificar retry interno do SDK | Correlação de trace não é identidade de tentativa de escrita. Usar token próprio por operação, preservado nas retentativas internas. |

## Blueprint proposto

1. Autenticar parceiro e validar contrato e chave. Manter compatibilidade com UUIDs atualmente aceitos; UUID v4 não é requisito universal de idempotência.
2. Construir identidade de operação com tenant, endpoint/versionamento e chave. Alterar chave física exige migração e compatibilidade com itens existentes, dispatcher e worker.
3. Calcular fingerprint de uma representação canônica dos campos de negócio validados: CPF, convênio, portabilidade, valor e parcelas. Normalizar valor decimal para evitar diferenças apenas de formatação. Não calcular sobre ciphertext aleatório. Avaliar HMAC-SHA-256 para reduzir exposição por tentativa de adivinhação de campos pessoais; não registrar payload ou fingerprint em logs.
4. Na ingestão assíncrona, gravar atomicamente a aceitação e o payload durável. O inbox já cumpre esse papel: não adicionar lock separado que crie outra janela de falha. Aceitação HTTP concluída é diferente de negócio concluído.
5. Em colisão, comparar tenant e fingerprint antes de responder. Mesma operação retorna 202 conforme contrato; conteúdo diferente retorna 422. O cache precisa preservar e conferir o fingerprint, inclusive em chamadas simultâneas.
6. Definir se será mantido `DUPLICATE_ACCEPTED` ou replay exato da resposta original. A resposta atual muda o campo de status; isso é uma decisão de contrato, não necessariamente duplicação de efeito.
7. No worker, adquirir processamento por transição condicional com proprietário e validade. Usar versão/token de fencing para impedir que um executor antigo finalize após perder a posse. Recuperar leases vencidos com verificação do efeito no destino.
8. Enviar sempre a mesma identidade de negócio ao legado. Timeout depois de commit é resultado incerto: consultar ou repetir com a mesma chave; nunca gerar outra chave para contornar a incerteza.
9. Persistir resultado terminal e evento de negócio atomicamente. Confirmar mensagem SQS somente após persistência durável; duplicatas terminais não devem repetir efeito ou contabilização.
10. Definir retenção a partir da janela máxima de retry, replay de DLQ, reconciliação e exigências do negócio. Não apagar automaticamente a proteção de uma operação ainda pendente.

## Critérios de aceitação

- Mesma chave e payload, sequencialmente e em paralelo: uma aceitação durável e um efeito de negócio.
- Mesma chave e payload diferente: 422, tanto com cache quente quanto após expiração do cache.
- Mesma chave em parceiros diferentes: comportamento conforme escopo documentado, sem exposição cruzada.
- Falha de resposta após commit do DynamoDB: retry recupera operação original.
- Duplicata SQS concorrente em duas réplicas: proteção do processamento e uma transição terminal.
- Queda antes/depois da chamada ao legado, antes/depois da gravação terminal e antes do ack: recuperação segura.
- Reinício do worker e do destino: estado e deduplicação permanecem dentro da retenção acordada.
- Emissão de métricas/eventos concorrentes: uma contabilização por operação terminal; não tratar contador em memória como razão financeira.
- Expiração lógica e exclusão física de TTL: testar separadamente, sem permitir novo efeito por exclusão prematura.

## Observações sobre a skill

A referência usa a expressão exactly-once de forma ampla. Chave, cache ou lock isolado não garantem esse resultado entre banco, fila e serviço externo. A garantia depende dos limites transacionais, da deduplicação no destino e da recuperação de resultados incertos.

O middleware Express/Redis apresentado é ilustrativo: expiração de lock sem token de propriedade, remoção incondicional e gravação assíncrona da resposta precisam de correção antes de uso crítico. Não deve ser transplantado para esta arquitetura.

## Referências

- [DynamoDB TTL](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/TTL.html): exclusão de itens expirados pode levar dias.
- [SQS: entrega pelo menos uma vez](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues-at-least-once-delivery.html): o consumidor deve suportar mensagens repetidas.

Primeira evolução recomendada: fingerprint e rejeição de divergência, definindo antes o escopo tenant/chave. Em seguida, persistência e controle de concorrência do worker.
