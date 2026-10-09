---
name: borda-absorcao-invariantes
description: "Use SEMPRE que uma mudança tocar ingestion-api, dispatcher-lambda, worker, mock-legacy, shared/contract ou a tabela DurableInbox: garante as invariantes da PoC (nenhuma proposta perdida, nenhuma averbação duplicada, legado nunca acima de 180 TPS, prioridade 70/30 sem fome). Também para revisar PRs e implementar as melhorias M1–M9 de docs/melhoria-proposta.md."
---

# Borda de Absorção: invariantes da PoC

## Escopo

Esta skill protege **as garantias que a PoC promete**. As skills horizontais (arquitetura, LGPD, testes, resiliência) continuam valendo, mas uma mudança pode passar em todas elas e ainda quebrar uma garantia. Esta skill existe para pegar esse caso.

Fontes de verdade:
- `docs/pattern-borda-de-absorcao.md`: as regras do pattern (o porquê).
- `docs/melhoria-proposta.md`: as melhorias M1–M9 e a seção 0, com o que foi aceito e o que foi rejeitado.
- `references/invariantes-e-testes.md`: o mapa invariante → código → teste, o glossário de estados e as notas de AWS.

## Fluxo de trabalho

1. **Identifique as invariantes tocadas.** Consulte a tabela de `references/invariantes-e-testes.md` pelos arquivos alterados.
2. **Verifique se a mudança cai numa decisão pendente** (seção "Decisões pendentes" abaixo). Se cair, **pare e pergunte ao usuário**. Não escolha um valor por conta própria.
3. **Confira as proibições** (seção "Nunca faça") contra o diff.
4. **Escreva primeiro os testes exigidos** para cada invariante tocada: teste de propriedade com jqwik quando a invariante vale "para qualquer entrada", e teste de integração quando depende de DynamoDB ou SQS.
5. **Implemente com a menor mudança** que mantém a invariante.
6. **Rode os gates do repositório:** `mvn verify` (cobertura ≥ 80%, Spotless, Enforcer, ArchUnit) e `mvn verify -Pintegration` quando houver adaptador DynamoDB ou SQS.
7. **Atualize a documentação:**
   - a coluna "Situação" da tabela "Mapeamento" em `docs/pattern-borda-de-absorcao.md`;
   - os checkboxes de critério de aceite em `docs/melhoria-proposta.md`;
   - o `aidlc-docs/aidlc-state.md`, conforme o `CLAUDE.md`.

## Invariantes (resumo)

| ID | Invariante | Situação |
|---|---|---|
| I1 | A borda faz **uma única escrita durável** e responde `202`. Ela não chama fila, legado nem banco relacional | implementado |
| I2 | **Idempotência técnica:** escrita condicional pela chave do parceiro | implementado, falta escopo por tenant (M1) |
| I3 | Mesma chave com **conteúdo diferente** → `422`, sem item novo | proposto (M1) |
| I4 | **Identidade de negócio:** fingerprint HMAC gravado na mesma transação | proposto (M1), escopo pendente |
| I5 | O dispatcher roteia **apenas `INSERT` de itens de proposta**. `MODIFY`, `REMOVE` e itens de fingerprint são ignorados | parcial: falta ignorar fingerprint (M1) |
| I6 | O legado **nunca** recebe mais de 180 TPS: `legacy.peak.window ≤ 180` | implementado, teto estático por instância (M4) |
| I7 | **QoS 70/30 com piso e empréstimo:** nenhuma lane passa fome | implementado |
| I8 | **Estados terminais são absorventes:** depois de `CONFIRMED` ou `FAILED`, o legado nunca é chamado de novo | implementado |
| I9 | **Retentativa só pela fila**, com backoff e DLQ. Nunca há retry dentro do processo na chamada ao legado | implementado |
| I10 | **Resultado desconhecido** (timeout depois do envio) → consultar o legado antes de reenviar | proposto (M2) |
| I11 | **Exclusão entre instâncias do worker** por lease condicional com fencing | proposto (M3). Até lá, o worker roda com **1 instância** |
| I12 | **Revalidação no ponto de efeito**, imediatamente antes de `LegacyCorePort.submit`, com evento auditável | proposto (M5) |
| I13 | O **resultado é publicado pelo log de estado**: o worker nunca chama o parceiro | proposto (M9) |
| I14 | **O CPF existe só cifrado no DurableInbox.** Mensagem de fila, log, métrica e trace nunca o carregam | implementado |
| I15 | Toda transição de estado gera um `ProposalEvent` | implementado |

## Nunca faça

- Chamar fila, legado ou qualquer serviço síncrono na borda além do DurableInbox e da cota (viola I1).
- Gravar no DurableInbox sem `conditionExpression` (viola I2).
- Devolver dados de outra proposta (`tracingId`, `receivedAt`) a um tenant diferente do dono da chave (viola I2 e I14).
- Adicionar retry, `@Retry` ou laço de reenvio no `LegacyHttpAdapter` ou no `ProcessProposalService` (viola I9 e I10).
- Criar transição que saia de `CONFIRMED` ou `FAILED`, ou chamar o legado quando `proposal.isFinal()` (viola I8).
- Consumir permissão de vazão fora do `ThroughputBudgetPort`, ou chamar `LegacyCorePort.submit` sem passar pelo laço de despacho (viola I6).
- Alterar `worker.budget.fast-share` ou o algoritmo do `LaneBudgetAllocator` sem teste de propriedade de "sem fome" (viola I7).
- Rodar mais de uma instância do worker enquanto o `ProposalRepositoryPort` for em memória (viola I11).
- Fazer o worker chamar webhook ou API do parceiro (viola I13).
- Colocar decisão (`reason_code`) no `202` ou na mensagem do SQS (seção 0 da melhoria proposta).
- Colocar CPF ou payload em mensagem de fila, log, tag de métrica ou atributo de span (viola I14; use `lgpd-sre-compliance-skill`).
- Mudar a chave da tabela `DurableInbox` sem migrar `InboxAttributes`, `DynamoDbInboxAdapter`, `DynamoDbInboxReadAdapter`, `LocalDynamoDb` e os testes de integração na mesma mudança.
- Renomear estados existentes. Estados novos seguem o glossário em `references/`.
- Trocar DynamoDB + Streams por Outbox + Debezium/Kafka na borda (decisão rejeitada, ADR na seção 10 da melhoria proposta).
- Tocar em `infrastructure/`: o autor está montando essa pasta por conta própria.

## Decisões pendentes: pare e pergunte

Não invente estes valores. Pergunte ao usuário no formato de `*-questions.md` do AI-DLC, com uma opção recomendada:

1. **Escopo do fingerprint (I4):** por tenant ou global? (Recomendação: por tenant na PoC.)
2. **Campos do fingerprint:** o payload atual não tem matrícula. Usar CPF + convênio + valor + parcelas?
3. **Janela do fingerprint e validade da proposta na fila** (I4, I12): quantas horas?
4. **Contrato de consulta do legado (I10):** formato do `GET /averbacoes?idempotencyKey=` no `mock-legacy`.
5. **Algoritmo do limite adaptativo (M4):** AIMD sobre latência e `503`? Com quais limiares?
6. **Ritmo de execução:** implementar as melhorias em sequência ou parar para aprovação a cada uma? O padrão do `CLAUDE.md` é aprovação por unidade.

## Delegação

- Retry, timeout e circuit breaker em geral: `resilience-checker-skill`. As regras I9 e I10 desta skill prevalecem.
- PII e telemetria: `lgpd-sre-compliance-skill`.
- Hexagonal e ArchUnit: `architecture-guidance`.
- Testes: `testing-strategy-guidance`.
- Provar números com carga e caos: `carga-caos-evidencia`.
- Revisão de PR contra estas invariantes: agente `garantia-auditor`.
