# Estado atual e limitações

Referência: 18/10/2026. Este documento registra evidências e pendências; não é monitoramento em tempo real. O [relatório de fechamento](sre/fechamento-poc-sre-observabilidade.md) delimita a conclusão da PoC de SRE.

## Implementação e validação

Validação histórica: havia registro de 356 testes aprovados; esse número fixo não representa o resultado atual. A última verificação ampla disponível encontrou timeout no teste concorrente `DynamoDbInboxAdapterIT`; testes focados de Business Fingerprint passaram. Consulte os relatórios da execução e o CI antes de declarar o build verde. O ambiente local sobe de ponta a ponta (`scripts\local-up.ps1`), com o fluxo proposta → fila → worker → legado verificado. Ajustes de desempenho e resiliência feitos em 09/10/2026: pool HTTP do DynamoDB, concorrência do worker, ZGC na borda e back-pressure com o circuito aberto. Quatro dashboards do Grafana e sete regras SRE foram publicados. Business Fingerprint v2 está implementado como opt-in e permanece desativado. Pendentes: homologação de carga de 10.000 TPS, demais cenários controlados de tracing e runtime Lambda na AWS, aplicação/importação do Terraform de alertas e as evoluções M1 a M9 de [plano de melhorias](brainstorming/melhoria-proposta.md).

## Observabilidade

O [contrato SRE v1](sre/contrato-medicao.md), o [avaliador offline](sre/relatorio-local.md) e a [captura local de JMeter/Inbox/worker](sre/captura-local.md) foram implementados. Nesta evolução passaram 48 testes focados e duas integrações; dois experimentos com JMeter real comprovaram reconciliação sem contar replays duas vezes e classificação de pendências vencidas durante indisponibilidade do legado. O relatório separa sucesso HTTP, decisão no prazo e pendências, com saídas JSON/Markdown/Prometheus e resultado incompleto quando falta cobertura. A captura é opt-in, local, para experimento isolado com uma JVM. Estado durável e projeção operacional contínua permanecem pendentes no [plano SRE](sre/plano-implementacao.md).


Na execução local, os serviços enviam métricas ao Pushgateway da EC2 e o Prometheus coleta esse gateway. A raspagem direta de `/actuator/prometheus` é uma alternativa quando os serviços são alcançáveis. A stack na EC2 contém Prometheus, Pushgateway, Grafana, Jaeger e Alertmanager. Alertas SRE seguem por SNS → SQS. CloudWatch tem configuração versionada, mas seu funcionamento no ambiente ativo precisa de validação. A instrumentação e a propagação W3C foram acrescentadas localmente em 17/10/2026; uma jornada real com retentativa foi comprovada no Jaeger, com 13 spans e quatro serviços no mesmo trace. Os vínculos foram verificados; duplicidade, rejeição comercial e correlação de logs de todos os serviços permanecem como exercícios seguintes. Ver o [guia de tracing local](manuais/tracing-local.md).

- Configuração do Grafana e mapa de métricas: [applications/observability/](../applications/observability/)
- Os seis dashboards, incluindo os dois novos SRE e a correção do backlog, foram sincronizados na EC2 em 17/10/2026. A API do Grafana confirmou os seis UIDs e o datasource; 108 expressões passaram no Prometheus. O relatório real de três propostas e três replays foi publicado com cobertura completa: R$ 3.000,00 recebidos/confirmados e aceite correto de 100%, mas falha nas metas de latência (5/6 até 100/200 ms). Os prazos de decisão da política padrão ainda não haviam maturado no corte. O primeiro teste teve timeout e foi preservado. As aplicações ficaram ativas durante essa rodada e foram encerradas posteriormente, junto com Docker e EC2. Ver o [guia SRE](sre/README.md).
- Stack e Terraform: [infrastructure/](../infrastructure/)

## Limites de uso

Alertas revisados em 17/10/2026: sete regras saudáveis, 14 cenários / 98 verificações
aprovados e contexto de dashboard/Jaeger nas annotations. Um alerta sintético com expiração
automática comprovou o ciclo de disparo e recuperação até o SNS pelos contadores de
notificações concluídas do Alertmanager, sem falhas reportadas. O SQS não foi inspecionado
nesta rodada por escolha do usuário. Ver [evidências e teste reproduzível](../infrastructure/Configuration/observability/ALERTAS.md).

A meta de 10.000 req/s com p95 abaixo de 100 ms ainda requer homologação. O caminho padrão não comprova exactly-once entre réplicas e reinícios. Business Fingerprint v2 permanece opt-in e desativado. EC2 e aplicações foram desligadas ao fim da sessão; consultar o ambiente antes de novos testes.

- [Auditoria de idempotência](auditoria-idempotencia.md).
- [Business Fingerprint v2](business-fingerprint-v2-implementacao.md).
- [Auditoria documental](auditoria-documentacao.md).
