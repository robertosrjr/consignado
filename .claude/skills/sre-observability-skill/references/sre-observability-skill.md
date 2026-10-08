# SRE Observability: roteiro de revisão integrada

Roteiro para avaliar logs, métricas e traces em conjunto, com base nas diretrizes de SRE do Google, OpenTelemetry e Prometheus. Esta skill coordena: a implementação e os exemplos de código ficam nas skills especialistas indicadas em cada passo.

## 1. Padrões de referência

- **Logs:** JSON estruturado (ECS), respeitando orçamento de log e privacidade → `spring-logging-skill`.
- **Métricas:** os **Four Golden Signals** → `spring-metrics-skill`.
  1. **Latência:** duração das respostas, em histogramas com buckets adequados (ex.: `[0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0]` segundos).
  2. **Tráfego:** demanda sobre o serviço (ex.: contagem de requisições HTTP).
  3. **Erros:** taxa de falhas, separando erros de sistema e de negócio.
  4. **Saturação:** uso de recursos limitados (ex.: pool de threads, conexões).
- **Traces:** contexto propagado no padrão W3C (`traceparent`) → `spring-tracing-skill`.
- **Privacidade:** minimização e mascaramento → `lgpd-sre-compliance-skill`.
- **Proteção de dependências:** timeouts, retries e circuit breakers observáveis → `resilience-checker-skill`.

## 2. Roteiro de revisão

### Passo 1: inventário
- [ ] Bibliotecas de telemetria no classpath (SLF4J/Logback, Micrometer, Micrometer Tracing/OpenTelemetry).
- [ ] Saídas brutas no console (`System.out`, `System.err`, `printStackTrace()`).
- [ ] Configuração de logs estruturados, exportadores (Prometheus, OTLP) e amostragem.

### Passo 2: mapa de lacunas
- [ ] Os quatro sinais têm medição nas rotas HTTP e nas chamadas a dependências?
- [ ] Um mesmo request é rastreável entre log, métrica e span (trace ID e span ID no MDC)?
- [ ] A propagação de contexto cobre as fronteiras de rede e de fila?

### Passo 3: riscos
- [ ] Cardinalidade: nenhum ID de usuário, UUID, email ou URL com parâmetros dinâmicos como tag de métrica.
- [ ] Privacidade: nenhum payload, valor financeiro ou dado pessoal em log, atributo de span ou tag.
- [ ] Orçamento de log: nada de log em laço de alto volume; níveis coerentes.
- [ ] Duplicação: nada de instrumentação manual para o que o Spring Boot já fornece.

### Passo 4: proposta e verificação
- [ ] Propor a menor mudança que fecha a lacuna de maior valor, no formato "Antes vs. Depois".
- [ ] Compilar e rodar testes focados.
- [ ] Registrar riscos residuais e impacto nos SLOs.
