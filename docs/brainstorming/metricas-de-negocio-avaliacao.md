# Avaliação das métricas de negócio da PoC

Base: `metricas-de-negocio-poc.md`, confrontada com o contrato e o código atuais.

## Veredito por métrica

| # | Métrica proposta | Veredito | Como é medida (nome no Prometheus) |
|---|---|---|---|
| 1 | VGV aprovado e averbado | **Implementada** | `business_vgv_sum{outcome="confirmed",convenio}` (soma = R$, `_count` = operações) |
| 2 | Ticket médio | **Implementada (derivada)** | `rate(business_vgv_sum) / rate(business_vgv_count)` |
| 3 | VGV preservado na virada | **Implementada** | `business_vgv_received_sum` (borda, só 202 novos); janela de 5 min com `increase(...[5m])` |
| 4 | Conversão por canal/parceiro | **Implementada** | `business_partner_settled_total{outcome="confirmed"} / business_partner_received_total`, por `tenant` |
| 5 | Orçamento QoS fast vs standard | **Implementada** | `business_vgv_sum` por `lane` + `worker_dispatched_total{lane}` (já existia) |
| 6 | Net backlog growth / drain time | **Já existia** | `net_backlog_growth`, `effective_drain_time` |
| 7 | Primeira passagem | **Implementada, com ressalva** | `business_first_pass_total{result}`: "primeira tentativa" vs "reenvio"; ver ressalva |
| 8 | Rejeição por idempotência | **Já existia** | `ingestion_accepted_total{outcome="duplicate_accepted"}` sobre o total |
| 9 | Throttling por tenant (429) | **Já existia** | `ingestion_throttled_tenant_total{tenant}` |
| 10 | Volume de portabilidade | **Parcial** | `business_vgv_*` com tag `portability`; falta saldo devedor |
| 11 | Spread médio / CET | **Não implementada** | o contrato não tem taxa de juros |

## Ressalvas (o que a métrica proposta não consegue ser hoje)

- **Primeira passagem (7):** o documento fala em rejeição por margem ou biometria. O legado simulado só devolve "rejeitada" com um motivo livre, e o motivo não vira tag (cardinalidade). A métrica entregue mede quantas averbações saíram na primeira tentativa, sem reenvio por falha transitória. É útil para a PoC, mas não é a métrica de negócio do texto.
- **Portabilidade (10):** há `portability` no contrato, mas só o valor da proposta. Saldo devedor, Open Finance e prazos regulatórios do BCB exigem campos que não existem. Fica o recorte por flag.
- **Spread/CET (11):** não há taxa no payload nem no legado. Exige mudança de contrato (`interestRate`); recomendo adiar.
- **Parceiro (4):** a tag `tenant` segue a allowlist (`platform.metrics.tenant-allowlist`); fora dela agrega em `other`. Defina a lista dos parceiros reais do teste.
- **VGV na borda (3):** é o valor aceito, não "garantido pelo legado". A garantia vem da durabilidade no DynamoDB, que é o que a PoC prova.
- **Consistência (1 vs 3):** as duas métricas vivem em serviços diferentes (ingestion e worker). A diferença entre elas é o VGV ainda na fila.

## Métricas adicionadas além do documento

| Métrica | Por quê |
|---|---|
| `business_time_to_settle_seconds` (histograma, lane) | Tempo do aceite na borda até o estado final no legado, com baldes de 30 s, 1, 5, 15 e 30 min. É o SLA real da virada: a PoC só prova a absorção se o backlog esvaziar em tempo previsível. Usa o instante de criação do item no inbox. |
| `business_vgv{outcome="rejected"}` | VGV rejeitado pelo legado, para o painel mostrar perda de receita separada da averbada. |

## Decisões de implementação

- **Sem PII:** nenhuma métrica leva CPF; só convenio, lane, portabilidade, resultado e parceiro.
- **Cardinalidade:** `business_vgv` tem no máximo 24 séries (2 resultados × 3 convênios × 2 lanes × 2 flags). Parceiro passa pela `TenantTagPolicy`.
- **Contagem exata:** o fato é emitido uma vez, na transição para estado terminal. Reentrega de mensagem de proposta já final não conta de novo (coberto por teste).
- **Ingestão:** só `RECEIVED` soma VGV. Duplicatas ficam em `ingestion_accepted_total{outcome="duplicate_accepted"}`.
- **Dinheiro em `double`:** aceitável para métrica agregada. O valor da proposta continua em `BigDecimal` no domínio.

## Onde está no código

- Worker: `BusinessMetrics`, porta `BusinessObserver`, fato `ProposalSettlement`, emitido em `ProcessProposalService`.
- Ingestion: `MeteredAcceptProposalUseCase` (`business.vgv.received`, `business.partner.received`).
- Testes: `BusinessMetricsTest`, `BusinessSettlementTest`, `ProposalControllerTest`.

## Pendente

- Painel do Grafana com estas consultas (U7). As consultas PromQL acima servem de base.
- Decidir sobre a taxa (CET) e o saldo devedor, que exigem mudar o contrato.
