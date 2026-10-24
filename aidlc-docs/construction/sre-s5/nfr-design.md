# S5 — Desenho não funcional

- [x] DynamoDB: partição por proposta, item STATE e eventos na mesma transação; leitura consistente para reentrega.
- [x] Índice esparso para órfãos, lote limitado e carência para reduzir disputa com Streams.
- [x] Retry após resposta perdida: consultar o estado pela mesma chave; não inventar nova identidade financeira.
- [x] Decisão na Inbox recuperada de estado terminal persistido; recorder condicional fornece idempotência.
- [x] Testar timeout pós-commit, reinício lógico do worker, falha de publicação e reconciliação real: build completo com integração aprovado.

O histórico transacional fornece a trilha durável; não há publicador externo de eventos nesta onda. Notificações e métricas de negócio em memória não possuem garantia de entrega exatamente uma vez.
