# Tracing local da jornada de propostas

Implementação iniciada em 16/10/2026 e retomada em 17/10/2026. A exportação OTLP e uma jornada real com retentativa foram verificadas no Jaeger da EC2 em 17/10/2026, no escopo de aplicação local e dispatcher local descrito abaixo.

## Instrumentação

Os serviços Spring usam `spring-boot-starter-opentelemetry`, com Micrometer Tracing e o SDK gerenciados pelo Spring Boot. A instrumentação HTTP de entrada permanece a do framework, sem filtro de tracing duplicado.

O módulo `platform-telemetry` concentra propagação W3C e spans de infraestrutura. O dispatcher sem Spring tem seu próprio SDK, com o mesmo protocolo e política de sampling configurável. Não há agente Java adicionado por esta implementação.

| Etapa | Instrumentação e contexto |
|---|---|
| API / mock legado | Spans HTTP do Spring; extração do contexto remoto pelo framework |
| Escrita da Inbox | Span CLIENT `put inbox`; `traceparent` e `tracestate` são gravados junto do item |
| Stream | Mapeadores Lambda e local recuperam somente os dois headers W3C da imagem |
| Publicação SQS | Span PRODUCER `send proposal`, filho do contexto da Inbox; headers do produtor vão nos atributos SQS |
| Worker | Solicita os atributos e guarda o contexto com cada mensagem no buffer; cria CONSUMER `process proposal` dentro da thread de processamento |
| Retentativa | Cada processamento recebe um span novo, mantendo a origem da mensagem; um evento registra o reagendamento |
| Chamada HTTP ao legado | Span CLIENT `POST /v1/averbacoes`, com injeção W3C explícita no cliente JDK existente |

O consumidor usa o contexto da mensagem como pai e registra um link para essa origem. A espera em fila não mantém o span HTTP de aceite aberto. Mensagens antigas sem contexto e headers inválidos iniciam uma nova raiz; não se inventa um pai a partir de um UUID comercial.

O cliente JDK do legado foi instrumentado explicitamente, preservando os testes, timeouts e circuit breaker existentes. Não adicionar instrumentação automática ou outro interceptor sobre esse cliente sem evitar spans duplicados.

## Identidade e privacidade

`tracingId` e `X-Trace-ID` continuam sendo identificadores de correlação da aceitação, reutilizados na resposta de duplicata. Eles são independentes do trace OTel. Isso impede que duas requisições que compartilham um trace sejam confundidas com a retentativa de uma única escrita do SDK.

O contexto de execução preenche `traceId`/`spanId` no MDC dos spans manuais e restaura os valores anteriores ao terminar. Os spans HTTP usam a correlação gerenciada pelo Spring. Baggage não é persistido na Inbox nem repassado pelo SQS/cliente manual.

Não são adicionados CPF, payloads, valores monetários, tokens, chaves, fingerprints ou identificadores de proposta aos atributos dos spans manuais. Exceções registram tipo e status de erro, sem mensagem ou stack trace. O mascaramento dos logs não é usado como proteção para traces. Rejeições esperadas de negócio não são marcadas automaticamente como falhas no span de processamento.

## Executar sem coletor

O SDK funciona sem exportador de rede enquanto `PLATFORM_OTLP_TRACES_ENDPOINT` não estiver definido. Os testes usam spans em memória e HTTP de loopback. Métricas e Pushgateway mantêm configuração própria.

Na pasta `applications`:

```powershell
mvn -o -pl platform-telemetry,platform-starter,ingestion-api,dispatcher-lambda,worker,local-env -am spotless:apply test '-Dtest=TraceContextTest,PlatformDefaultsEnvironmentPostProcessorTest,PlatformAutoConfigurationTest,SmallAdaptersTest,SystemTracingIdAdapterTest,DynamoDbInboxAdapterTest,ProposalControllerTest,AwsAdaptersTest,StreamTraceContextTest,InboxStreamHandlerTest,DispatchServiceTest,SqsLaneQueueAdapterTest,LegacyHttpAdapterTest,ProposalDispatchLoopTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

O modo `-o` exige dependências previamente disponíveis no cache Maven. Sem elas, remover `-o` e resolver as dependências pela configuração Maven autorizada.

O teste de timeout do cliente usa uma exceção injetada e verifica o timeout configurado na requisição, evitando depender da velocidade da estação. Os testes HTTP funcionais usam servidor de loopback e prazo próprio de fixture; eles não homologam latência de produção. Nenhum timeout da aplicação foi aumentado para acomodar a execução dos testes.

## Evidências locais de 17/10/2026

- 97 testes focados aprovados: telemetria (5), starter (10), API (26), dispatcher (23) e worker (33).
- `DispatcherEndToEndIT` aprovado com DynamoDB Local e ElasticMQ: os headers W3C chegam às filas, cada produtor recebe novo span ID, `tracestate` é preservado e baggage não é incluído.
- JARs dos serviços gerados pelo reactor, incluindo o mock legado e as bibliotecas de runtime do executor local.
- Gate de cobertura JaCoCo do novo módulo `platform-telemetry` aprovado, usando os dados dos testes focados.

Essa validação não equivale a uma execução completa de todos os testes do projeto. A primeira rodada encontrou instabilidade nos testes HTTP com prazo de parede; a fixture foi separada da verificação determinística do timeout, e os testes do worker passaram na revalidação. O teste de fila confirma as mensagens consumidas para que a verificação de não republicação não dependa de seu visibility timeout.

Para repetir a integração sem executar toda a suíte de integração da aplicação, na pasta `applications`:

```powershell
mvn -o -P integration -pl local-env -am spotless:apply test-compile failsafe:integration-test failsafe:verify '-Dit.test=DispatcherEndToEndIT' '-Dfailsafe.failIfNoSpecifiedTests=false' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Antes de subir a aplicação com os novos JARs, reconstruir o reactor com `mvn install -DskipTests`. Esse comando empacota e instala módulos; não comprova testes aprovados.

## Configurar o envio ao Jaeger

Quando o coletor estiver disponível, definir no terminal que iniciará os processos:

```powershell
$env:PLATFORM_OTLP_TRACES_ENDPOINT = 'http://<ENDERECO_DO_COLETOR>:4318/v1/traces'
$env:PLATFORM_TRACING_SAMPLING = '1.0'
.\scripts\local-up.ps1
```

O endpoint é OTLP HTTP de traces, não `/v1/metrics`. O mesmo valor configura o exportador Spring e o dispatcher standalone. `PLATFORM_TRACING_SAMPLING` é uma probabilidade entre 0 e 1 e respeita a decisão do pai remoto. Usar 100% para testes controlados; dimensionar sampling e retenção para carga real.

O iniciador também aceita `-OtlpTracesEndpoint 'http://<ENDERECO_DO_COLETOR>:4318/v1/traces'`.
O parâmetro explícito prevalece sobre a variável de ambiente; sem ambos, reutiliza o último
endpoint salvo em `applications/.local/observability.json`, junto do Pushgateway. Para executar
sem exportação de traces, passar explicitamente `-OtlpTracesEndpoint ''`.

No Lambda, as mesmas variáveis configuram o SDK. Há flush limitado a um segundo ao terminar a invocação; lifecycle, freeze e conectividade do ambiente Lambda ainda exigem validação no runtime AWS.

O iniciador local usa processos separados. Seu script de desligamento encerra processos à força; aguardar o envio do lote antes de desligar evita perder os últimos spans ainda em memória. O término forçado não oferece garantia de flush.

## Jornada verificada no Jaeger em 17/10/2026

Foi inspecionado um trace real já recebido durante a sessão, sem gerar novas propostas ou alterar o processamento:

- Trace ID: `b3bfe7a7c587e4763a544cdb8a5c8c75`.
- Início: 17/10/2026 às 17:16:56, America/Sao_Paulo.
- Treze spans, uma raiz, quatro serviços e duração total observada de aproximadamente 6,31 s.
- Todos os spans referenciados foram encontrados no mesmo trace; não houve referências órfãs nessa amostra.
- A resposta inicial da API foi HTTP 202; a primeira chamada ao legado teve erro técnico, e a segunda retornou HTTP 200.

| Etapa | Evidência observada |
|---|---|
| API | `http post /v1/proposals`, HTTP 202, aproximadamente 48,58 ms. |
| Inbox | `put inbox` no serviço `ingestion-api`, aproximadamente 13,78 ms. |
| Dispatcher/fila | `send proposal` em `dispatcher-local`, referenciando o span da Inbox. |
| Primeira tentativa do worker | `process proposal`, receive count 1, com evento de retentativa. |
| Primeira chamada ao legado | `POST /v1/averbacoes`, erro `LegacyUnavailableException`, aproximadamente 1.047,28 ms. O tipo observado não identifica sozinho a causa raiz. |
| Segunda tentativa do worker | Outro span `process proposal`, receive count 2, relacionado à mesma publicação. |
| Segunda chamada ao legado | Span CLIENT no worker, HTTP 200, aproximadamente 19,21 ms; span SERVER em `mock-legacy`, aproximadamente 14,32 ms, referenciando o cliente. |

Na representação retornada pelo Jaeger, os dois consumidores têm referência `FOLLOWS_FROM`
à publicação da fila. Os demais vínculos observados são `CHILD_OF`. Não exigir que o span HTTP
da API permaneça aberto até o fim da jornada: o trabalho assíncrono continua após o aceite.

Logs da API e do mock legado também foram encontrados com esse trace ID. A presença de logs
do worker nesse trace específico não foi comprovada; seus spans foram encontrados e ligados.
O JSON original foi preservado em `applications/.local/jaeger-journey-evidence.json`, ignorado
pelo Git. A retenção do Jaeger é limitada: esse exemplo pode deixar de estar disponível no futuro.

Para abrir a amostra, usar a interface do Jaeger na porta 16686 e o caminho
`/trace/b3bfe7a7c587e4763a544cdb8a5c8c75`. Para encontrar novas jornadas:

1. Selecionar **Service: worker** e **Operation: process proposal**.
2. Ajustar o período para incluir o teste e clicar em **Find Traces**.
3. Abrir um resultado e conferir os quatro serviços, a publicação e cada tentativa.
4. Expandir um span com erro e consultar tipo/status; comparar com o span da tentativa seguinte.

## Limites e próximos exercícios

Esta amostra comprova recepção OTLP e continuidade da jornada com retentativa no Jaeger.
Ainda não homologa o dispatcher Lambda executando na AWS, todos os caminhos de erro ou a
correlação de logs de todos os serviços. O sucesso HTTP no legado não prova sozinho commit
financeiro único. Nenhuma garantia de exactly-once ou durabilidade do worker é acrescentada.

Os próximos exercícios devem usar massa controlada para: proposta sem retentativa, duplicata,
rejeição comercial e indisponibilidade do legado. Conferir causalidade, novos spans por
tentativa, eventos de reagendamento e atributos exportados em cada cenário. A revisão de
privacidade deve incluir os spans automáticos do framework; a lista de atributos manuais
não equivale a uma auditoria de todos os valores exportados.

O modo Business Fingerprint usa o mesmo mapeamento dos atributos da Inbox para carregar contexto, mas sua ativação e recursos AWS permanecem uma entrega separada.

Referências: [Spring Boot Tracing](https://docs.spring.io/spring-boot/4.1/reference/actuator/tracing.html), [OpenTelemetry Traces](https://opentelemetry.io/docs/concepts/signals/traces/) e [spans de mensageria](https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/).
