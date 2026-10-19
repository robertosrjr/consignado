# ADR-002: Smart Idempotency por Hash SHA-256 de Negócio

> Nota de escopo — revisão de 16/10/2026: Este documento registra uma especificação ou decisão de arquitetura. Metas, componentes target e declarações de aprovação não comprovam implementação ou homologação da PoC atual. Consulte o [índice documental](../README.md) e as limitações atuais antes de usar este texto como descrição do ambiente.


**Status:** Approved  
**Data:** 2026-10-14  
**Decisores:** Staff Architecture Board, Security & FinOps Lead  

## Contexto e Declaração do Problema
Robôs e scripts automatizados de correspondentes bancários retransmitem a mesma proposta com novos UUIDs de cabeçalho (`Idempotency-Key`) ao sofrerem timeouts. O modelo de idempotência tradicional baseado no header falha, permitindo inserções duplicadas no banco NoSQL e gerando custos desnecessários em nuvem (FinOps).

## Decisão Recomendada
Substituir a dependência do UUID do cliente por uma impressão digital determinística do contrato de negócio:
`BusinessHash = SHA256(CPF | Convenio | Valor | Parcelas | DataUTC)`

Gravar atomicamente no Amazon DynamoDB (`DurableInbox`) utilizando `PK = HASH#<BusinessHash>` e expressão condicional `attribute_not_exists(PK)`.

## Consequências
* **Positivas**: Descarte de duplicatas em < 15 ms na borda do NoSQL; custo zero de filas SQS ou escritas no PostgreSQL para requisições duplicadas.
* **Negativas**: Exige higienização e formatação rigorosa dos dados imutáveis do contrato antes do cálculo do hash.
