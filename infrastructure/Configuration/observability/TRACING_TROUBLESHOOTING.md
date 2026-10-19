# Diagnóstico de tracing: OpenTelemetry e Jaeger

Revisão: 17/10/2026. O Jaeger recebeu uma jornada real com API, Inbox, dispatcher local, worker, legado e retentativa no mesmo trace. São 13 spans com referências resolvidas; a primeira chamada ao legado falhou e a seguinte retornou HTTP 200. Evidência, limites e comandos: [guia de tracing](../../../docs/manuais/tracing-local.md).

## Estado atual

- O starter usa a integração OpenTelemetry do Spring Boot; exportação HTTP OTLP é opcional e ativada por endpoint explícito.
- `SystemTracingIdAdapter` gera UUID de correlação da aceitação, independente do trace OTel.
- Inbox, Stream e SQS transportam `traceparent`/`tracestate`; o worker restaura o contexto por mensagem na thread de processamento.
- O cliente HTTP JDK do legado tem span CLIENT e injeção W3C explícitos.
- Ter constantes para esses headers ou ver serviços de teste no Jaeger não comprova propagação ponta a ponta.

## Verificar o backend

Na EC2:

```sh
cd /home/ubuntu/observability
docker compose -f compose.sem-elasticsearch.yaml ps
docker logs --tail 100 jaeger
curl -fsS http://127.0.0.1:16686/api/services
```

A interface fica em `http://<IP_PUBLICO_ATUAL>:16686`. Consultar o IP atual da EC2; não reutilizar endereços de execuções antigas. Uma lista de serviços vazia pode ser esperada após limpeza ou sem spans recentes.

O receiver configurado oferece OTLP gRPC em 4317 e OTLP HTTP em 4318. Para OTLP HTTP de traces, o caminho é `/v1/traces`; `/v1/metrics` pertence a outro sinal. Um GET em `/v1/traces` não é teste válido de exportação: um erro HTTP pode mostrar que há um servidor respondendo, mas não prova ingestão de spans.

`localhost` aponta para o namespace do processo que envia. É válido no mesmo host quando a porta está publicada; dentro de containers diferentes, normalmente deve ser usado o nome do serviço/rede apropriado. Para emissão pela estação local, usar o endereço alcançável da EC2 ou um túnel SSH. Restringir o acesso de rede aos emissores necessários.

## Instrumentar antes de diagnosticar exportação

Escolher uma estratégia compatível de instrumentação: agente OpenTelemetry ou integração SDK/bridge na aplicação. Não combinar mecanismos que gerem spans duplicados para a mesma operação. Acrescentar apenas `opentelemetry-exporter-otlp` não garante autoconfiguração completa.

Preservar contexto de tracing na gravação da Inbox, na leitura do Stream e no envio SQS; extrair e ativar contexto por mensagem no worker, incluindo seus executores. Propagar contexto na chamada HTTP ao legado. Modelar relações pai/filho ou links conforme a semântica assíncrona e os lotes; não manter um span HTTP aberto durante toda a espera em fila.

Não colocar CPF, payload, token ou segredo em atributos, eventos ou baggage. O mascaramento do JSON de logs não protege automaticamente spans exportados.

## Critério de conclusão

1. Emitir uma proposta controlada e identificar seu trace ou os traces relacionados por links.
2. Conferir API, passagem assíncrona, processamento do worker e chamada ao legado.
3. Verificar IDs, relacionamentos, duração, atributos e correlação dos logs com o contexto ativo.
4. Repetir com retentativa e duplicidade; cada tentativa precisa manter identidade e causalidade corretas.
5. Verificar falhas técnicas separadamente de rejeições de negócio esperadas.

## Referências

- [Conceitos de traces](https://opentelemetry.io/docs/concepts/signals/traces/).
- [Propagação de contexto](https://opentelemetry.io/docs/concepts/context-propagation/).
- [Convenções de spans de mensageria](https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/).

## Obstáculos e pendências

A conectividade/exportação OTLP e uma jornada real com retentativa foram verificadas no
Jaeger em 17/10/2026. Permanecem os exercícios controlados de duplicidade, rejeição comercial
e falhas, a correlação de logs de todos os serviços e a validação do runtime Lambda na AWS.
O Jaeger ter uma porta aberta ou listar serviços isolados continua sem comprovar esses cenários.
