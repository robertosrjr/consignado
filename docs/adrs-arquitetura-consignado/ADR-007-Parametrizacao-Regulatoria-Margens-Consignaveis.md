# ADR-007: Parametrização Regulatória das Margens Consignáveis

> Nota de escopo — revisão de 16/10/2026: Este documento registra uma especificação ou decisão de arquitetura. Metas, componentes target e declarações de aprovação não comprovam implementação ou homologação da PoC atual. Consulte o [índice documental](../README.md) e as limitações atuais antes de usar este texto como descrição do ambiente.


**Status:** Approved  
**Data:** 2026-10-14  
**Decisores:** Staff Architecture Board, Legal & Compliance Lead  

## Contexto e Declaração do Problema
O arcabouço regulatório de crédito consignado passa por frequentes atualizações normativas (ex: limites de margem INSS de 45%, redução gradual para 40% na MP 1.355/2026, prazos de 84 parcelas e descontos eSocial rubrica 9253). Alterações hardcoded no código geram riscos operacionais e necessidade de deploys de emergência.

## Decisão Recomendada
Isolar todas as regras de margem, prazos e convênios em tabelas de parâmetros dinâmicos consumidas pelo Fast-Fail Filter Engine e pelos Workers, auditáveis por data de vigência e órgão emissor.

## Consequências
* **Positivas**: Atualização instantânea de regras regulatórias via configuração; conformidade legal auditável; facilidade na transição de teto de margem.
* **Negativas**: Exige controle rigoroso de versionamento e governança na atualização dos arquivos de parâmetros.
