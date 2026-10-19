# ADR-006: Write Sharding (Partition Salting) no Amazon DynamoDB

> Nota de escopo — revisão de 16/10/2026: Este documento registra uma especificação ou decisão de arquitetura. Metas, componentes target e declarações de aprovação não comprovam implementação ou homologação da PoC atual. Consulte o [índice documental](../README.md) e as limitações atuais antes de usar este texto como descrição do ambiente.


**Status:** Approved  
**Data:** 2026-10-14  
**Decisores:** Staff Architecture Board, Principal Cloud Engineer  

## Contexto e Declaração do Problema
O pico de 10.000 TPS de escrita concentrado em chaves de partição com padrões repetitivos gera o fenômeno de *Hot Partitions* no DynamoDB, ultrapassando o limite físico de 1.000 WCU por partição e provocando exceções de throttling.

## Decisão Recomendada
Aplicar a técnica de Write Sharding (Partition Salting) adicionando um sufixo numérico distribuído à Partition Key do DynamoDB:
`PK = HASH#<BusinessHash>#PARTITION_<0..9>`

Distribuir uniformemente a escrita de 10.000 TPS entre 10 partições físicas distintas do DynamoDB.

## Consequências
* **Positivas**: Eliminação absoluta de Hot Partitions e exceções de throttling; suporte a aumentos de carga lineares.
* **Negativas**: Consultas agregadas por hash de negócio exigem padrão Scatter-Gather em paralelo entre as 10 partições.
