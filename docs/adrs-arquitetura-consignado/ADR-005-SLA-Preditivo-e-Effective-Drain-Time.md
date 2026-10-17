# ADR-005: Engine de Previsão de SLA & Effective Drain Time

> Nota de escopo — revisão de 16/10/2026: Este documento registra uma especificação ou decisão de arquitetura. Metas, componentes target e declarações de aprovação não comprovam implementação ou homologação da PoC atual. Consulte o [índice documental](../README.md) e as limitações atuais antes de usar este texto como descrição do ambiente.


**Status:** Approved  
**Data:** 2026-10-14  
**Decisores:** Staff Architecture Board, SRE Lead  

## Contexto e Declaração do Problema
Respostas `HTTP 202 Accepted` sem estimativa de conclusão geram incerteza nos correspondentes bancários, incentivando o disparo duplicado de robôs para instituições concorrentes.

## Decisão Recomendada
Injetar nos cabeçalhos e no payload do `HTTP 202` o cálculo preditivo do tempo de esvaziamento da fila:
`DrainTime = (QueueDepth_lane + P_relative) / ProcessingRate_lane + Latency_external`

Expor o acompanhamento do progresso em tempo real através de Webhooks assíncronos e conexões leves Server-Sent Events (SSE).

## Consequências
* **Positivas**: Transparência operacional total; desincentivo ao re-envio de propostas para bancos concorrentes; redução de chamadas de polling.
* **Negativas**: Margem de erro em estimativas durante instabilidades imprevisíveis nos sistemas do governo.
