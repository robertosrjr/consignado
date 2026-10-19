# ADR-003: Engine de Triagem Rápida em Memória (Fast-Fail Filter Engine)

> Nota de escopo — revisão de 16/10/2026: Este documento registra uma especificação ou decisão de arquitetura. Metas, componentes target e declarações de aprovação não comprovam implementação ou homologação da PoC atual. Consulte o [índice documental](../README.md) e as limitações atuais antes de usar este texto como descrição do ambiente.


**Status:** Approved  
**Data:** 2026-10-14  
**Decisores:** Staff Architecture Board, Compliance Lead  

## Contexto e Declaração do Problema
Milhares de propostas sabidamente inelegíveis (benefícios INSS bloqueados pela trava de 90 dias da DDB - IN 138/172, estouro do teto de margem da MP 1.355/2026 ou parceiros suspensos pela Febraban) são enviadas no pico, consumindo recursos de nuvem e ocupando o buffer limitado de 180 TPS do legado.

## Decisão Recomendada
Embarcar na Ingestion API um motor de triagem em memória (Caffeine Cache / ElastiCache Redis) que executa 5 Guardrails determinísticos em < 5 ms:
1. Validação estrutural/assinatura do token pré-aprovado.
2. Sanitização e dígito verificador do CPF.
3. Checagem de bloqueio DDB de 90 dias do INSS.
4. Validação do teto de margem e parcelas (MP 1.355/2026 e Portaria MTE 240).
5. Sanção e status de bloqueio do correspondente na Febraban/ABBC.

Rejeitar instantaneamente propostas inválidas com `HTTP 422 Unprocessable Entity` em formato RFC 7807 Problem Details.

## Consequências
* **Positivas**: Retenção de 20% a 30% do tráfego inelegível na borda; zero custo de persitência no DynamoDB/SQS; preservação do orçamento de 180 TPS para contratos válidos.
* **Negativas**: Requer sincronização contínua e assíncrona do cache em memória com os cadastros de regras dos órgãos oficiais.
