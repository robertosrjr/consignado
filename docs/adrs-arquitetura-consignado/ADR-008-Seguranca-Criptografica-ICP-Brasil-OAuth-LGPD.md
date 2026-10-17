# ADR-008: Segurança Criptográfica ICP-Brasil, OAuth 2.0 e Rastreabilidade LGPD

> Nota de escopo — revisão de 16/10/2026: Este documento registra uma especificação ou decisão de arquitetura. Metas, componentes target e declarações de aprovação não comprovam implementação ou homologação da PoC atual. Consulte o [índice documental](../README.md) e as limitações atuais antes de usar este texto como descrição do ambiente.


**Status:** Approved  
**Data:** 2026-10-14  
**Decisores:** Staff Architecture Board, Chief Information Security Officer (CISO)  

## Contexto e Declaração do Problema
Comunicações com órgãos oficiais (Dataprev, SERPRO, MTE e eSocial) exigem autenticação via certificado digital ICP-Brasil (A3/HSM) e assinaturas OAuth 2.0. Expirações de certificados ou falhas de auditoria de consentimento no Open Finance acarretam sanções do Banco Central e interrupção do serviço.

## Decisão Recomendada
1. Centralizar a gestão de chaves e certificados ICP-Brasil no AWS Secrets Manager e AWS KMS com alertas automatizados de renovação antecedente em 30 dias.
2. Implementar log de auditoria imutável dos consentimentos e acessos a dados no Amazon Aurora PostgreSQL em conformidade com a LGPD e resoluções do BCB.

## Consequências
* **Positivas**: Zero downtime por expiração de certificado digital; auditoria completa e pronta para fiscalizações do BCB/ANPD.
* **Negativas**: Custo adicional com módulos HSM/KMS e gerenciamento de ciclo de vida de credenciais.
