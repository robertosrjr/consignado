# APIs, configurações e dependências

Data: 17/10/2026. Valores abaixo são a baseline de código; o preflight deve conferir overrides antes de qualquer execução.

## Interfaces e topologia

| Interface | Uso no experimento | Cuidado |
|---|---|---|
| ingestion-api, porta 8080 | Entrada de propostas e resposta de aceite | Usar o contrato e autenticação do JMX versionado; preservar chave idempotente nos replays. |
| mock-legacy, porta 8081, `POST /v1/averbacoes` | Chamada downstream com API key e `Idempotency-Key` | Replay pode responder com `X-Replayed`; confirmar uma única entrada no ledger. |
| Serviço JWT, porta 8082 | Autenticação do cenário local | Não incluir tokens nas evidências. |
| worker, porta 8083 | Processamento e Actuator | Não reiniciar o processo para simular recuperação sem perda de estado. |
| DynamoDB Local 8000 / ElasticMQ 9324 | Inbox e filas locais | Não confundir essas filas com a SQS de alertas na AWS. |
| Grafana 3000 / Prometheus 9090 / Alertmanager 9093 | Visualização e detecção | Conferir endereço atual após eventual início da EC2. |
| Pushgateway 9091 / OTLP HTTP 4318 / Jaeger 16686 | Métricas e traces | Endpoint OTLP de traces termina em `/v1/traces`; verificar frescor e exportação. |

## Fontes para a Construction

| Fonte | Decisão suportada |
|---|---|
| [Código do worker](../../../../applications/worker) | `WorkerProperties`, `LegacyHttpAdapter`, `CircuitBreakerAvailabilityAdapter` e `RetryBackoff`. |
| [Código do mock](../../../../applications/mock-legacy) | `LegacyProperties` e `AverbacaoController`: caos antes do registro no ledger. |
| [Ambiente local](../../../../applications/local-env) | `LocalSqs`: nomes das filas, visibilidade e redrive. |
| [Inicializador local](../../../../applications/scripts/local-up.ps1) | Ciclo dos processos, configuração e chaves por sessão. |
| [Regras Prometheus](../../../../infrastructure/Configuration/observability/config/prometheus/rules/consignado-sre.yml) | Condições, frescor e duração dos sete alertas atuais. |
| [Alertas e runbooks](../../../../infrastructure/Configuration/observability/ALERTAS.md) | Diagnóstico, rota SNS e testes de notificação. |
| [JMeter](../../../../jMetter/consignado/Thread%20Group.jmx) | Contrato de entrada e carga existente. |

## Baseline de resiliência

| Parâmetro | Valor observado | Consequência |
|---|---|---|
| Timeout HTTP do legado | 3 s | Resposta perdida pode coexistir com efeito concluído no servidor. |
| Slow-call | 2 s | Lentidão afeta o circuito antes de necessariamente produzir timeout. |
| Circuit breaker | Falhas 10%; mínimo 20 chamadas; janela 100 | Cinco propostas não garantem abertura do circuito. |
| OPEN / HALF_OPEN | Espera 10 s; transição automática; 5 chamadas permitidas em HALF_OPEN | Backpressure bloqueia consumo em OPEN; em PROBING há uma chamada em voo. |
| Retry | Base 5 s, exponencial, teto 60 s | Conferir recebimentos e relógio; não inferir uma duração fixa até a DLQ. |
| Filas locais | `sqs-fast-lane-queue`, `sqs-standard-lane-queue`, `sqs-dlq` | Visibilidade 30 s; redrive `maxReceiveCount=3`. |
| Caos do mock | Latência 0; taxa de erro 0 por padrão | Configuração de inicialização; nenhum endpoint runtime é presumido existente. |
| Alerta do legado | Circuito OPEN + backlog + push fresco, por 2 min | Pode reiniciar o período pendente nas transições do circuito. |

Dependências centrais no parent: AWS SDK 2.55.12, Resilience4j 2.4.0, ElasticMQ 1.7.1 e DynamoDB Local 2.6.1. Não há atualização de versões neste plano.

## Contratos a preservar ou projetar

- A identidade canônica e a chave idempotente devem sobreviver a retry e redrive. Preservar envelope criptografado, contexto de sessão e `traceparent`/`tracestate` quando presentes; não gerar nova proposta no redrive.
- Não pressupor suporte do ElasticMQ a `StartMessageMoveTask`. Validar a API antes de escolher redrive automático; alternativa é transferência controlada com envio confirmado antes da exclusão da mensagem de origem.
- Um futuro controle de falha deve operar somente no perfil de teste, restrito a loopback, com proteção de acesso, prazo máximo e restauração automática. Seu contrato ainda será desenhado e testado.
- Se necessário, criar campo/header técnico distinto para o trace OTel. Não alterar o significado atual de `X-Trace-ID`/`tracingId` nem usar esses IDs como labels de métricas.
