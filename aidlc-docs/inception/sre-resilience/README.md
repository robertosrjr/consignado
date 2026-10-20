# Incremento AI-DLC: demonstrar detecção e recuperação

Revisão: 18/10/2026. Estado: **ENCERRADO COMO PoC**. R0 e L1 foram concluídos; L2 ficou incompleto na conciliação final; L3 comprovou a proteção local e revelou uma lacuna do detector. R2 a R4 foram movidos para o backlog.

A Inception foi concluída e o usuário autorizou a continuidade: “seguimos o plano do ia-dlc então”. O [desenho R0](../../construction/sre-r0/unit-design.md) detalha a primeira unidade; o [guia local](../../../docs/sre/preparacao-falhas-legado.md) documenta seu uso. Após a autorização de início em 18/10, o [R1/L1 foi executado](../../construction/sre-r1/validation.md). O escopo foi encerrado como PoC; a continuidade depende de uma nova fase. Consulte o [relatório de fechamento](../../../docs/sre/fechamento-poc-sre-observabilidade.md).

Este incremento brownfield aplica o AI-DLC Lite à PoC existente. A prioridade é demonstrar uma falha do legado, sua detecção e a recuperação das propostas usando os componentes disponíveis. Depois seguem DLQ, carga progressiva e resposta a incidentes. Não cria SLA contratual nem declara os experimentos executados.

## Artefatos da Inception

| Artefato | Finalidade |
|---|---|
| [Estado atual](reverse-engineering/system-overview.md) | Arquitetura, evidência existente e limitações. |
| [APIs e dependências](reverse-engineering/api-and-dependencies.md) | Contratos, configurações e pontos que exigem evolução. |
| [Requisitos](requirements/requirements.md) | Escopo, critérios de aceite e requisitos não funcionais. |
| [Plano de execução](plans/execution-plan.md) | Ordem de implementação, experimentos, validação, riscos e rollback. |

O fluxo adotado foi textual: detecção do workspace → engenharia reversa incremental → requisitos → planejamento → revisão → Construction → validação operacional. Foram aplicadas as regras locais em `.aidlc-lite-rule-details/`, com profundidade Standard, e as skills locais de SRE e engenharia do caos. Os caminhos `.kiro`/`.amazonq` citados em `CLAUDE.md` não são a localização efetiva dessas regras neste workspace.

User Stories foram dispensadas porque este incremento é operacional e não altera a jornada de negócio. Application Design e Units Generation foram detalhados para R0 no desenho da unidade; as próximas unidades serão detalhadas quando iniciadas. O [resultado R0](../../construction/sre-r0/validation.md) distingue validação local da execução integrada ainda pendente.

Este diretório complementa o [roadmap SRE](../../../docs/sre/plano-implementacao.md) e preserva os artefatos históricos da Inception original. A fonte de verdade deste incremento é o plano aqui vinculado; os índices de SRE apenas o referenciam.

A Inception foi documental. O ambiente foi iniciado em 18/10 mediante autorização; o L1 executou uma falha real e restaurada no mock, com evidências no Jaeger e snapshot SRE publicado. Ver limites e estado no relatório da unidade.
