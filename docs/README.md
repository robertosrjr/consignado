# Documentação da plataforma de consignado

Comece pelo [estado atual](estado-atual.md). Brainstormings, SDDs e relatórios antigos preservam propostas e evidências das respectivas datas; não são o plano ativo. MFA e aprovação transacional foram excluídos da PoC.

Revisão: 23/10/2026. Este índice separa os guias da implementação, as propostas de evolução e as evidências históricas. O código e a configuração versionados definem o que está implementado; a existência de um SDD ou ADR não comprova implantação ou homologação.

## Guias da implementação

| Documento | Público e finalidade |
|---|---|
| [README principal](../README.md) | Visão geral e início da execução |
| [Contexto e objetivos](narrativas/contexto-e-objetivos.md) | Motivação de negócio, metas e critérios de homologação |
| [Visão técnica](manuais/visao-tecnica.md) | Arquitetura, stack, decisões e privacidade |
| [Ambiente local](manuais/ambiente-local.md) | Emuladores, ciclo de vida e envio de métricas |
| [Estado atual](estado-atual.md) | Evidências disponíveis, entregas e limitações |
| [Fechamento do primeiro ciclo SRE (18/10)](sre/fechamento-poc-sre-observabilidade.md) | Conclusão, evidências, limites e backlog da observabilidade |
| [Tracing local](manuais/tracing-local.md) | Jornada local de quatro serviços validada no Jaeger; limites no runtime AWS e em cenários ainda não exercitados |
| [Aplicações](../applications/README.md) | Build, módulos e ambiente local |
| [Jornada da proposta](jornadas/jornada-da-proposta.md) | Explicação para negócio |
| [Jornada entre módulos](jornadas/jornada-entre-modulos.md) | Fluxo técnico e contratos |
| [Manual da API](narrativas/manual-api-rest.md) | Integração e testes HTTP; permanece nesta pasta para preservar referências |
| [Infraestrutura](../infrastructure/README.md) | Stack da EC2 e limites da automação |
| [Dashboards](../applications/observability/grafana/dashboards/README.md) | Consultas, filtros, precisão e cores |
| [SRE da PoC](sre/README.md) | Ferramentas, dashboards, catálogo de métricas, SLIs, SLOs e limites do SLA |
| [Plano atual S0–S7](../aidlc-docs/inception/sre-evolucao/workflow-plan.md) | Entregas S0–S5 e homologação pendente da S6 |
| [Capacidade local](sre/capacidade-local.md) | Método, resultados e limitações dos ensaios S6 |
| [Plano AI-DLC de resiliência SRE](../aidlc-docs/inception/sre-resilience/plans/execution-plan.md) | Plano encerrado no escopo da PoC; resultados de R0/R1 e backlog de R2-R4 |
| [Contrato de medição SRE](sre/contrato-medicao.md) | Elegibilidade, identidade, coorte e cálculo offline dos SLIs |
| [Relatório local SRE](sre/relatorio-local.md) | Avaliador Java, entradas normalizadas e demonstração reproduzível |
| [Captura local SRE](sre/captura-local.md) | Integração opt-in JMeter/Inbox/worker com controle de sessão e cobertura |
| [Exercício real de falha do worker](sre/exercicio-falha-worker.md) | Pausa controlada, alerta real, SNS, recuperação de propostas e traces |
| [Falha transitória do legado — L1](sre/exercicio-falha-legado-l1.md) | Retry 503→200, cinco confirmações e distinção entre recuperação e metas de latência |
| [Roteiro da apresentação SRE](sre/apresentacao/README.md) | Preparação, falas, telas, comandos e encerramento da demonstração |
| [Alertas](../infrastructure/Configuration/observability/ALERTAS.md) | Regras e integração Alertmanager → SNS → SQS |
| [Tracing](../infrastructure/Configuration/observability/TRACING_TROUBLESHOOTING.md) | Estado atual e roteiro de diagnóstico |

## Implementações e evidências específicas

| Documento | Como interpretar |
|---|---|
| [Auditoria de idempotência](auditoria-idempotencia.md) | Diagnóstico do caminho padrão e riscos de persistência/concorrência |
| [Business Fingerprint v2](business-fingerprint-v2-implementacao.md) | Implementação opt-in; flag desligada, recursos AWS ainda não provisionados |
| [Base para artigo SRE](artigos/base-artigo-sre-observabilidade-consignado.md) | Fotografia dos testes realizados, não inventário em tempo real |
| [Revisão documental de 23/10](auditoria-documentacao-2026-10-23.md) | Correções desta revisão, verificações e limites |
| [Auditoria de 18/10](auditoria-documentacao-2026-10-18.md) | Registro histórico da revisão anterior |
| [Auditoria documental de 16/10](auditoria-documentacao.md) | Fotografia histórica da revisão anterior |

## Propostas, especificações e histórico

- [Pattern Borda de Absorção](patterns/pattern-borda-de-absorcao.md): arquitetura reutilizável; garantias dependem dos adaptadores e do ambiente.
- [Brainstorming](brainstorming/): avaliações de métricas e plano de melhorias; não são especificações do estado implantado.
- [SDDs](sdd/): a especificação inicial da PoC e as arquiteturas target de LaaS descrevem escopos diferentes. Documentos v2/v3/v4 registram evoluções; não executar instruções de versões diferentes como um único plano.
- [Narrativas](narrativas/): contexto de negócio e cenários ilustrativos. Números de mercado e referências regulatórias exigem fontes e data de consulta antes de publicação.
- [ADRs](adrs-arquitetura-consignado/): decisões e propostas da evolução arquitetural; verificar o código e a evidência de entrega de cada item.
- [Regras](rules/): requisitos e instruções recebidos; não comprovam que cada requisito foi implementado.
- [AI-DLC](../aidlc-docs/): registros históricos de processo e aprovações. Não substituir registros antigos por uma afirmação de validação atual.

Os SDDs do orquestrador de agentes tratam de outro subsistema, além da aplicação de consignado. Não devem ser usados como descrição dos serviços Java existentes.

## Obstáculos e pendências

- A jornada local de quatro serviços foi observada no Jaeger. Ainda faltam validação no runtime Lambda/AWS e cobertura de todos os caminhos de erro.
- O build completo de 23/10 passou com 473 testes. A capacidade permanece sem homologação: a S6 reprovou p99 mesmo a 1 req/s; a meta de 10.000 req/s não foi comprovada.
- A próxima entrega segue o plano S0–S6: investigar e repetir a qualificação da S6. SDDs target e ADRs de evolução não ampliam automaticamente o escopo.
- Links locais foram verificados; disponibilidade de páginas externas e correção integral de fontes de mercado/regulação não foram auditadas.
