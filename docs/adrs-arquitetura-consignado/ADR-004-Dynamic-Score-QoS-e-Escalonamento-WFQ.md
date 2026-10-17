# ADR-004: Algoritmo de Priorização Dinâmica (Dynamic Score QoS)

> Nota de escopo — revisão de 16/10/2026: Este documento registra uma especificação ou decisão de arquitetura. Metas, componentes target e declarações de aprovação não comprovam implementação ou homologação da PoC atual. Consulte o [índice documental](../README.md) e as limitações atuais antes de usar este texto como descrição do ambiente.


**Status:** Approved  
**Data:** 2026-10-14  
**Decisores:** Staff Architecture Board, Business Product Owner  

## Contexto e Declaração do Problema
No pico de 10.000 TPS, propostas de alto VGV, portabilidades de crédito (Resoluções CMN 4.292/13 e 15/2025) e clientes VIP correm o risco de retenção prolongada em filas FIFO, aumentando a probabilidade de perda de margem para concorrentes.

## Decisão Recomendada
Calcular o `Dynamic Score` de prioridade em milissegundos na borda:
`Score = (0.40 * VGV_norm) + (0.35 * Rating_VIP) + (0.25 * RiscoConcorrencia * Mult_OpenFinance)`

Distribuir as mensagens em 3 faixas de prioridade no SQS com orçamento de capacidade proporcional (Weighted Fair Queueing):
* **Ultra Fast Lane (Score >= 80)**: 50% da capacidade (90 TPS).
* **Fast Lane (50 <= Score < 80)**: 30% da capacidade (54 TPS).
* **Standard Lane (Score < 50)**: 20% da capacidade (36 TPS).

Incluir mecanismo anti-starvation por tempo de retenção (`Aging Promotion`).

## Consequências
* **Positivas**: Garantia de SLA < 2 min para operações estratégicas e portabilidades; otimização da rentabilidade do banco.
* **Negativas**: Requer monitoramento ativo das métricas de profundidade de fila e ajuste fino de pesos.
