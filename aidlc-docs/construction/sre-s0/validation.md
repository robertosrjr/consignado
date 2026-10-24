# S0 — fechar o legado da PoC

Data: 23/10/2026. Execução 100% local, sem AWS (pedido do usuário).

| Requisito | Entrega | Validação |
|---|---|---|
| RF-01 L2 | Conciliação offline em `docs/sre/exercicio-falha-legado-l2.md`; resultado **INCOMPLETE** definitivo | Hashes e critérios conferidos contra artefatos preservados |
| RF-02 Detector | `ConsignadoLegacyUnavailable` passa a `estado > 0` sustentado 2 min com `max_over_time(...[2m]) == 2`; L3 "intermittent" agora espera disparo em 3m/4m | promtool 3.5.0 local: `check rules` 7 regras; `test rules` nos dois arquivos SUCCESS, inclusive "Meio aberto nao e aberto" |
| RF-03 pedidos-api | Removidos `rules/pedidos-api.yml` e `grafana/pedidos-api-dashboard.json`; `external_labels.monitor` = `consignado-monitor` | `promtool check config` SUCCESS |

Ferramenta: `C:\Developer\Programas\prometheus-3.5.0.windows-amd64\promtool.exe`, baixado do release oficial.

Limites: regra não publicada em Prometheus em execução; o teste L3 foi reescrito em JSON indentado (conteúdo igual, exceto as expectativas do caso alternado).

Reprodução, em `infrastructure/Configuration/observability/config/prometheus`:

```
promtool check rules rules/consignado-sre.yml
promtool test rules tests/consignado-sre.test.yml tests/consignado-l3.test.yml
```
