# Documentação da plataforma de consignado

Revisão: 16/10/2026. Este índice separa os guias da implementação, as propostas de evolução e as evidências históricas. O código e a configuração versionados definem o que está implementado; a existência de um SDD ou ADR não comprova implantação ou homologação.

## Guias da implementação

| Documento | Público e finalidade |
|---|---|
| [README principal](../README.md) | Visão geral e início da execução |
| [Contexto e objetivos](narrativas/contexto-e-objetivos.md) | Motivação de negócio, metas e critérios de homologação |
| [Visão técnica](manuais/visao-tecnica.md) | Arquitetura, stack, decisões e privacidade |
| [Ambiente local](manuais/ambiente-local.md) | Emuladores, ciclo de vida e envio de métricas |
| [Estado atual](estado-atual.md) | Evidências disponíveis, entregas e limitações |
| [Aplicações](../applications/README.md) | Build, módulos e ambiente local |
| [Jornada da proposta](jornadas/jornada-da-proposta.md) | Explicação para negócio |
| [Jornada entre módulos](jornadas/jornada-entre-modulos.md) | Fluxo técnico e contratos |
| [Manual da API](narrativas/manual-api-rest.md) | Integração e testes HTTP; permanece nesta pasta para preservar referências |
| [Infraestrutura](../infrastructure/README.md) | Stack da EC2 e limites da automação |
| [Dashboards](../applications/observability/grafana/dashboards/README.md) | Consultas, filtros, precisão e cores |
| [Alertas](../infrastructure/Configuration/observability/ALERTAS.md) | Regras e integração Alertmanager → SNS → SQS |
| [Tracing](../infrastructure/Configuration/observability/TRACING_TROUBLESHOOTING.md) | Estado atual e roteiro de diagnóstico |

## Implementações e evidências específicas

| Documento | Como interpretar |
|---|---|
| [Auditoria de idempotência](auditoria-idempotencia.md) | Diagnóstico do caminho padrão e riscos de persistência/concorrência |
| [Business Fingerprint v2](business-fingerprint-v2-implementacao.md) | Implementação opt-in; flag desligada, recursos AWS ainda não provisionados |
| [Base para artigo SRE](base-artigo-sre-observabilidade-consignado.md) | Fotografia dos testes realizados, não inventário em tempo real |
| [Auditoria documental](auditoria-documentacao.md) | Problemas encontrados, correções e pendências desta revisão |

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

- O tracing completo ainda precisa de instrumentação, propagação e teste ponta a ponta.
- Não há evidência atual de homologação a 10.000 TPS nem de execução completa de testes sem falhas.
- É necessário definir formalmente qual SDD target e quais ADRs orientam a próxima entrega. Esta revisão não revoga decisões de negócio nem exclui versões históricas.
- Links locais foram verificados; disponibilidade de páginas externas e correção integral de fontes de mercado/regulação não foram auditadas.
