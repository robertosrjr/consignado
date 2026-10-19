# Engenharia reversa incremental: estado atual

Data: 17/10/2026. Escopo: fontes versionadas e evidências documentadas; não é inspeção do ambiente em execução.

## Arquitetura observada

O [reator Maven](../../../../applications/pom.xml) contém nove módulos: `shared`, `platform-telemetry`, `platform-starter`, `platform-aws`, `mock-legacy`, `ingestion-api`, `dispatcher-lambda`, `worker` e `local-env`. A base é Java 25 e Spring Boot 4.1.1.

Fluxo local: JMeter → ingestion-api → Inbox DynamoDB Local → dispatcher → filas ElasticMQ fast/standard → worker → mock-legacy. O dispatcher é executado pela infraestrutura local; isso não comprova a execução de uma Lambda real na AWS.

Métricas seguem aplicação → Pushgateway → Prometheus → Grafana. Alertas seguem Prometheus → Alertmanager → SNS → subscription SQS. Traces W3C são exportados via OTLP para Jaeger. Logs estruturados complementam a investigação, com sanitização de PII.

| Componente | Responsabilidade e limite relevante |
|---|---|
| ingestion-api | HTTP 202, idempotência e Inbox. Aceite não equivale a confirmação. |
| dispatcher | Prioridade e roteamento para duas lanes. |
| worker | Consumo, backpressure, retry e circuit breaker. Repositório/outbox locais em memória. |
| mock-legacy | Capacidade nominal de 180 TPS, ledger idempotente e caos configurável na inicialização. Ledger em memória. |
| local-env | Emuladores e inicialização dos processos; chaves locais geradas por sessão. |
| platform-* / shared | Telemetria, integrações, segurança e contratos compartilhados. |

## O que já foi demonstrado

O [exercício do worker](../../../../docs/sre/exercicio-falha-worker.md) registra pausa controlada de 270 segundos, dez propostas novas e dez replays, alerta real de ausência de push, publicação firing/resolved até o SNS e confirmação das dez propostas após a retomada. As filas locais terminaram vazias e foram encontrados traces com os quatro serviços.

Essa evidência demonstra recuperação após pausa no mesmo processo. Não demonstra recuperação após perda de memória, persistência durável do worker ou entrega/consumo na fila SQS de alertas da AWS. A verificação mais recente de notificações foi limitada ao SNS por decisão do usuário.

O [guia de tracing](../../../../docs/manuais/tracing-local.md) documenta propagação e jornadas verificadas. Traces ajudam a explicar chamadas e tentativas; não são um livro contábil nem denominador completo de SLI quando há amostragem.

## Lacunas que determinam o plano

1. Não há controle de caos em runtime já comprovado, reversível e autenticado. Reiniciar o mock para mudar configuração perde o ledger e pode invalidar a comparação.
2. O circuito tenta sair de OPEN após dez segundos; o alerta de legado exige OPEN contínuo por dois minutos e backlog. É necessário medir se detecta uma indisponibilidade sustentada nas condições reais.
3. A fila tem redrive após três recebimentos. Uma falha prolongada pode levar propostas à DLQ antes de um alerta de backlog/legado maturar.
4. Profundidade, idade e frescor da DLQ ainda não têm cobertura operacional completa. Uma série ausente não pode ser apresentada como fila vazia.
5. Carga acima da cota por tenant pode produzir 429 antes de testar a capacidade do legado. A vazão configurada não comprova capacidade sustentável.
6. `X-Trace-ID` é correlação de negócio, não o identificador técnico do OpenTelemetry.

## Limites preservados

Não serão adicionados banco persistente do worker, novos serviços cloud, migração para ECS, Business Fingerprint v2 ou novos dashboards permanentes neste incremento. A rota de notificação continua nativa no Alertmanager/SNS, sem Lambda. Não haverá ampliação de IAM para consultar SQS apenas para esta demonstração.

O último encerramento documentado deixou processos locais, Docker remoto e EC2 parados. Nenhum desses estados foi alterado ou novamente consultado durante o planejamento.
