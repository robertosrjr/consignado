# Dashboards Grafana

Paineis sobre as metricas Micrometer dos servicos Java:

| Arquivo | Painel | Servicos |
|---|---|---|
| `ingestion-health.json` | Saude da Ingestao: vazao, p50/p95/p99, 202 x 429/503, VGV recebido, JVM | ingestion-api |
| `downstream-protection.json` | Protecao Downstream: teto de 180 TPS, lanes 70/30, circuit breaker, em voo, duplicadas | worker, mock-legacy |
| `backlog-drain.json` | Backlog e Drenagem: filas por lane, chegada x processamento, drenagem, SLO, VGV | worker |
| `business-overview.json` | Negocio: propostas novas, confirmadas/rejeitadas, valores e ticket medio no periodo | ingestion-api, worker |

Fonte de dados: Prometheus com `uid: prometheus`. Os filtros usam a tag `service` (comum a todas as
metricas, ver `platform-starter`) e a variavel `$instance`.

## Como as metricas chegam ao Prometheus

- Na AWS/EC2 com a aplicacao alcancavel: scrape de `/actuator/prometheus`.
- Carga local contra o Prometheus do EC2: Pushgateway. Suba com
  `scripts\local-up.ps1 -PushgatewayAddress <host>:9091`; cada servico envia a cada 10 s
  (`job` = nome do servico, `instance` = nome da maquina).

## Provisionamento

- Local: `../provisioning/dashboards/dashboards.yml` (pasta `/var/lib/grafana/dashboards`).
- EC2 de observabilidade: copiar os JSON para
  `~/observability/config/grafana/provisioning/dashboards/consignado/` junto com um provider
  apontando para `/etc/grafana/provisioning/dashboards/consignado`; o Grafana relê a cada 30 s.

Os JSON versionados sao a fonte de verdade. Editar os arquivos e publica-los no provisionamento;
alteracoes pela interface nao sao persistidas (`allowUiUpdates: false`).

## Indicadores de negocio

O painel `Consignado - Negocio` usa as metricas existentes, sem exigir reinicio da aplicacao.
Filtros: ambiente, instancia, convenio e portabilidade. Lane aparece no grafico de finalizacoes;
as metricas de entrada nao possuem essa tag.

| Indicador no periodo selecionado | Metrica / calculo |
|---|---|
| Propostas novas aceitas e valor recebido | `business_vgv_received_brl_count` / `_sum` |
| Pedidos confirmados e valor confirmado | `business_vgv_brl_count` / `_sum`, `outcome="confirmed"` |
| Pedidos rejeitados e valor rejeitado | `business_vgv_brl_count` / `_sum`, `outcome="rejected"` |
| Ticket medio recebido ou confirmado | soma dos incrementos de valor / soma dos incrementos de quantidade |
| Taxa de confirmacao | confirmados / (confirmados + rejeitados) |

Os indicadores usam `sum(increase(metrica[$__range]))`: ajustam resets por serie antes da soma.
Os graficos de evolucao usam `rate` com `$__rate_interval` e mostram propostas/s ou R$/s.
Ticket e taxa ficam sem dados quando o denominador e zero; nao se inventa um ticket de R$ 0.

Aceite na entrada significa somente proposta nova (`RECEIVED`); duplicatas nao inflam o valor.
Confirmacao e rejeicao representam resultados finais no legado. Retry, falha tecnica, limite de
tenant e erro HTTP nao sao rejeicoes de negocio. O painel nao mede recusas de validacao na entrada.
Nao somar valores recebidos e confirmados: sao etapas da mesma proposta. Recebimento e
finalizacao podem estar em periodos diferentes, portanto a taxa usa somente os finalizados.

As contagens sao estimativas de observabilidade (`increase` extrapola entre coletas), podendo
apresentar arredondamento. Medidores sao locais a JVM e lacunas/reinicios podem perder eventos;
o painel nao substitui conciliacao com a base transacional. No caminho com Pushgateway, preservar
`honor_labels: true` e usar `scrape_protocols: [PrometheusProto]` no job de coleta.

## Formatacao e cores do painel de negocio

Valores monetarios e tickets usam `currency:financial:R$`, sem abreviacao em K/M, com duas
casas decimais. Quantidades sao exibidas como inteiros.

Os paineis de confirmados comparam com os recebidos no mesmo periodo e filtros:

- Quantidade confirmada versus quantidade nova aceita: arredondamento para inteiros.
- Valor confirmado versus valor recebido: arredondamento para centavos.
- Ticket medio confirmado versus recebido: arredondamento para centavos.

Laranja indica confirmado menor; verde indica confirmado igual ou maior. A comparacao usa a
mesma precisao exibida para evitar cores divergentes por casas ocultas. As duas consultas sao
mutuamente exclusivas; a consulta B usa override laranja por `byFrameRefID`.

A cor nao mede so backlog nem atesta saude: rejeicoes e propostas recebidas/finalizadas em
periodos distintos tambem alteram a comparacao. Se faltar uma das series ou o denominador do
ticket for zero, o painel mostra sem dados. `increase` continua sendo estimativa, nao ledger.
