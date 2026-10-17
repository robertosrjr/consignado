# Estado atual e limitações

Referência: 16/10/2026. Este documento registra evidências e pendências; não é monitoramento em tempo real.

## Implementação e validação

Validação histórica: havia registro de 356 testes aprovados; esse número fixo não representa o resultado atual. A última verificação ampla disponível encontrou timeout no teste concorrente `DynamoDbInboxAdapterIT`; testes focados de Business Fingerprint passaram. Consulte os relatórios da execução e o CI antes de declarar o build verde. O ambiente local sobe de ponta a ponta (`scripts\local-up.ps1`), com o fluxo proposta → fila → worker → legado verificado. Ajustes de desempenho e resiliência feitos em 09/10/2026: pool HTTP do DynamoDB, concorrência do worker, ZGC na borda e back-pressure com o circuito aberto. Quatro dashboards do Grafana e sete regras SRE foram publicados. Business Fingerprint v2 está implementado como opt-in e permanece desativado. Pendentes: homologação de carga de 10.000 TPS, tracing distribuído, aplicação/importação do Terraform de alertas e as evoluções M1 a M9 de [plano de melhorias](brainstorming/melhoria-proposta.md).

## Observabilidade


Na execução local, os serviços enviam métricas ao Pushgateway da EC2 e o Prometheus coleta esse gateway. A raspagem direta de `/actuator/prometheus` é uma alternativa quando os serviços são alcançáveis. A stack na EC2 contém Prometheus, Pushgateway, Grafana, Jaeger e Alertmanager. Alertas SRE seguem por SNS → SQS. CloudWatch tem configuração versionada, mas seu funcionamento no ambiente ativo precisa de validação. O tracing distribuído completo ainda não está implementado e comprovado.

- Configuração do Grafana e mapa de métricas: [applications/observability/](../applications/observability/)
- Stack e Terraform: [infrastructure/](../infrastructure/)

## Limites de uso

A meta de 10.000 req/s com p95 abaixo de 100 ms ainda requer homologação. O caminho padrão não comprova exactly-once entre réplicas e reinícios. Business Fingerprint v2 permanece opt-in e desativado. EC2 e aplicações foram desligadas ao fim da sessão; consultar o ambiente antes de novos testes.

- [Auditoria de idempotência](auditoria-idempotencia.md).
- [Business Fingerprint v2](business-fingerprint-v2-implementacao.md).
- [Auditoria documental](auditoria-documentacao.md).
