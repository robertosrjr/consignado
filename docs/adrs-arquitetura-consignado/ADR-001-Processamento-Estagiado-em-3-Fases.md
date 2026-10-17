# ADR-001: Padrão de Processamento Estagiado em 3 Fases

> Nota de escopo — revisão de 16/10/2026: Este documento registra uma especificação ou decisão de arquitetura. Metas, componentes target e declarações de aprovação não comprovam implementação ou homologação da PoC atual. Consulte o [índice documental](../README.md) e as limitações atuais antes de usar este texto como descrição do ambiente.


**Status:** Approved  
**Data:** 2026-10-14  
**Decisores:** Staff Architecture Board, Principal Systems Engineer  

## Contexto e Declaração do Problema
Operações de crédito consignado exigem validações complexas de IA, leitura do Open Finance, KYC/PLD e biometria facial, que demandam entre 3s e 15s por requisição. No segundo 00:01 da "virada de folha", o tráfego atinge picos de 10.000 TPS. Executar essas validações síncronamente durante o pico inviabiliza o sistema e gera colapso de infraestrutura.

## Decisão Recomendada
Adotar um Padrão de Processamento Estagiado e Assíncrono em 3 Fases:
1. **Estágio 1 (Off-Pico / Durante o Dia)**: Qualificação do cliente via IA, Open Finance, KYC/PLD (Circular BCB nº 3.978) e pré-formalização do token da CCB Digital.
2. **Estágio 2 (Borda 00:01 / Ingestão Massiva)**: Recebimento do payload leve pré-aprovado, respondendo HTTP 202 Accepted em < 100 ms a 10.000 TPS no Amazon DynamoDB.
3. **Estágio 3 (Back-Office / Assíncrono)**: Consumo cadenciado da fila a 180 TPS via Workers ECS Fargate e Resilience4j Rate Limiter para averbação e liquidação (Pix/TED).

## Consequências
* **Positivas**: Isolamento da latência de terceiros no pico; p95 < 100 ms mantido na borda; proteção total do Core Banking legado a 180 TPS.
* **Negativas**: Aumento na complexidade de orquestração de eventos e necessidade de gerenciamento de estado entre estágios.
