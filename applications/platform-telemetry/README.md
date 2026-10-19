# platform-telemetry

Infraestrutura de tracing compartilhada, sem dependências de domínio, Spring ou AWS SDK.

- `TraceContext`: captura e extrai somente W3C Trace Context (`traceparent` e `tracestate`), sem baggage.
- `Telemetry`: spans de infraestrutura usando o SDK do processo, com restauração do contexto/MDC e registro de erro sem mensagem ou stack trace.
- `StandaloneTelemetry`: SDK do dispatcher sem Spring; exportador OTLP HTTP opcional, resource por serviço e sampling baseado no pai.

Os serviços Spring configuram a biblioteca com o SDK gerenciado pelo Boot. O dispatcher local/Lambda configura um SDK próprio. A configuração é única por processo; não representa suporte a vários SDKs de aplicações distintas dentro da mesma JVM.

Testes usam exportador em memória; não dependem da EC2. Configuração, limites e critérios da validação posterior: [guia de tracing local](../../docs/manuais/tracing-local.md).
