# S6 — Capacidade: ensaios executados, homologação pendente

Data: 23/10/2026. Escopo inteiramente local. Relatório completo: `docs/sre/capacidade-local.md`.

## Resultado

- [x] Envelope progressivo com fator de replay 1,25, 20 tenants e lanes alternadas.
- [x] Conciliação por STATE e averbações únicas; parada da progressão em erro ou drenagem incompleta.
- [x] Modelo de drenagem e limites físicos de 180 TPS documentados, sem alegar vazão homologada.
- [x] Coleta direta Prometheus real: quatro alvos UP, recording rules e alertas avaliados. Pushgateway e OTLP externos desligados.
- [x] Confirmar o resultado curto por 60 s: 5 req/s reprovou latência; confirmação conservadora a 1 req/s também reprovou p99 no servidor.
- [ ] Limite sustentável dentro de todos os critérios J1/J2: não determinado. DoD de homologação não atingido; não marcar S6 concluída.

## Evidências

Quatro relatórios imutáveis em `applications/.local/capacity-2026-10-23T23-10-34-497Z/`, `23-15-28-464Z/`, `23-25-16-674Z/` e `23-29-58-243Z/` (os três últimos também com prefixo `capacity-2026-10-23T`). Primeiro ensaio FAIL por 503; rodada progressiva INCOMPLETE na saturação. Ensaios longos PASS na conciliação de dados, mas reprovados em latência — os indicadores separados impedem confundir os dois resultados.

Última rodada: 96 propostas únicas, 96 averbações únicas e nenhuma resposta de erro; J2 no prazo. Cliente aquecido: p95 170 ms / p99 285 ms. Servidor: p95 estimado 89 ms / p99 245 ms; bucket exato de 200 ms indica 2,78% acima do limite, reprovando o máximo de 1%. Dados em `applications/.local/capacity-stack-20261023-202802/server-latency-final.json`.

Correção adicional após coleta real: regras J1 passam a produzir zero erro quando há tráfego elegível sem série 5xx, mantendo ausência de dado sem tráfego. Teste de regressão em `consignado-slo.test.yml`. Quatro suítes promtool (SLO, alertas SLO, SRE e L3) SUCCESS; configuração local válida, três arquivos de regras carregados. Scripts Node validados por `node --check`; orquestrador PowerShell validado pelo parser e executado em duas rodadas reais.

## Operação e continuidade

Processos Java/Prometheus iniciados para os ensaios foram encerrados; AWS não foi acessada. Dados locais de teste são efêmeros no DynamoDB em memória; relatórios e TSDB foram preservados.

Retomada: investigar etapas do aceite e recursos do host, corrigir pacing do gerador nos atrasos e repetir qualificação. Preservar SLOs atuais e resultados negativos. Aging não foi implementado: ausência de evidência de inanição não autoriza introduzi-lo.
