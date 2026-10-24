# Capacidade local — 23/10/2026

Ambiente: processos Java 25, DynamoDB Local em memória e ElasticMQ; Prometheus 3.5.0 com scrape direto. Sem Docker ou AWS. As metas de produção, inclusive 10 mil TPS, não foram homologadas.

## Método

Vinte tenants, lanes alternadas, uma repetição a cada cinco chamadas: fator de replay 1,25 e entrada única = 0,8 × vazão HTTP. Confirmação pela tabela STATE e pelo delta de averbações únicas do legado. Relatório não armazena CPF, payload ou tokens.

Latências abaixo são medidas pelo cliente. O SLO formal J1 usa observação no servidor; Prometheus coleta o histograma real e calcula os SLIs. PASS no campo `status` de um patamar significa conciliação de dados naquele patamar; os campos de latência, taxa e J2 têm avaliação separada.

## Rodadas

1. Rodada inicial: 10 req/s, 150 chamadas, 149 respostas 202 e um 503 por `ApiCallAttemptTimeoutException`. Foram observadas 120 averbações únicas para 119 chaves com resposta 202. Resultado FAIL; não caracteriza duplicidade financeira, pois houve resposta incerta de escrita. Arquivo original preservado em `applications/.local/capacity-2026-10-23T23-10-34-497Z/report.json`.
2. Rodada controlada: DEBUG herdado desativado e exportação OTLP de métricas desativada; timeouts do emulador local explicitados em 10 s/5 s, sem alterar defaults da aplicação. Oito patamares de 15 s nominais; duração efetiva registrada para expor limitação do gerador. Arquivo `applications/.local/capacity-2026-10-23T23-15-28-464Z/report.json`.
3. Confirmação de 60 s: aquecimento em 1 req/s e medição em 5 req/s. As 240 propostas únicas do segundo patamar confirmaram, com 240 averbações únicas, zero erros e J2 no prazo; p95 cliente 254 ms e p99 661 ms. O alvo de latência não se sustentou. Arquivo `applications/.local/capacity-2026-10-23T23-25-16-674Z/report.json`. Prometheus: disponibilidade J1 com erro zero, mas aproximadamente 11,46% dos aceites acima de 100 ms na janela observada; arquivo `.local/capacity-stack-20261023-202317/snapshot.json`.
4. Confirmação conservadora: dois patamares de 1 req/s por 60 s, primeiro de aquecimento. Todas as 96 propostas únicas confirmaram, com 96 averbações únicas e zero erros. No patamar aquecido, p95/p99 cliente = 170/285 ms; J2 fast/standard no prazo. Arquivo `applications/.local/capacity-2026-10-23T23-29-58-243Z/report.json`.

## Conclusão de homologação

**Nenhum limite sustentado foi homologado em todos os critérios de latência.** Houve conciliação de dados nos patamares baixos e em parte da saturação, mas o resultado curto de 5 req/s não se repetiu em 60 s.

No servidor, a última rodada teve disponibilidade J1 com erro zero e aproximadamente 2,78% dos aceites acima de 100 ms: o critério de 95% em 100 ms passou nessa janela. Entretanto, 2,78% também passaram de 200 ms, acima do máximo de 1%: o critério de p99 falhou. Quantis interpolados do histograma: p95 ≈ 89 ms, p99 ≈ 245 ms. Evidência: `applications/.local/capacity-stack-20261023-202802/server-latency-final.json`, consultada no instante original do snapshot, não após a parada.

A observação é curta e não substitui o SLO móvel de 30 dias. O limite operacional dentro dos SLOs permanece **não determinado**. A S6 tem ensaios e relatório entregues, mas seu critério de conclusão de homologação continua pendente.

Próxima investigação: separar custo de autenticação, escrita DynamoDB e agendamento do host; corrigir o pacing do gerador para não aguardar timer zero quando atrasado; repetir a qualificação com recursos do host controlados. Não alterar os SLOs para acomodar a falha. O host reportou 2 cores/4 processadores lógicos, cerca de 16 GiB de RAM e CPU em 100% após os ensaios; esses dados não isolam a causa do gargalo.

| Taxa solicitada | Taxa efetiva HTTP/s | p95 cliente (ms) | p99 cliente (ms) | Únicas confirmadas no corte | Drenagem após envios (s) |
|---|---:|---:|---:|---:|---:|
| 1 | 1,07 | 3.220 | 3.220 | 12/12 | 0,28 |
| 3 | 3,05 | 62 | 87 | 36/36 | 0,67 |
| 5 | 5,06 | 78 | 177 | 60/60 | 0,16 |
| 10 | 10,04 | 175 | 209 | 120/120 | 0,19 |
| 30 | 28,68 | 1.849 | 1.977 | 360/360 | 18,12 |
| 60 | 43,03 | 654 | 867 | 720/720 | 20,32 |
| 120 | 65,55 | 983 | 1.634 | 1.440/1.440 | 40,93 |
| 240 | 39,70 | 1.704 | 2.023 | 2.678/2.880 | 124,65 |

O primeiro patamar inclui aquecimento. Até 120 solicitadas, houve conciliação integral e zero respostas de erro na rodada controlada; em 60 e 120 a taxa desejada não foi atingida. O maior patamar curto que atendeu simultaneamente a taxa e os alvos de latência do cliente foi 5 req/s. Isso não equivale a capacidade sustentada de produção.

O reteste de 60 s reprovou 5 req/s em latência. Não promover o resultado curto a limite homologado. Uma medição adicional neste host executou 100 callbacks `setTimeout(0)` em 2.889 ms; o gerador usa um timer por envio, inclusive quando está atrasado. Há limitação do próprio gerador/scheduler nos patamares altos, além do custo dos emuladores e serviços. Esses patamares não determinam o limite máximo da aplicação.

Em 240 solicitadas, a espera de drenagem excedeu o corte nominal de 120 s devido também ao custo das consultas; a conciliação ficou incompleta e a lane fast ultrapassou o prazo de 2 min. STATE, ledger e Inbox foram lidos em instantes distintos enquanto havia processamento: a divergência entre os valores no corte não prova perda ou duplicidade. Resultado global da rodada: INCOMPLETE. Não substituir o arquivo original por um PASS posterior.

## Drenagem e J2

Modelo ideal sem entrada: `tempo = backlog / vazão terminal observada`. Com entrada contínua: `tempo = backlog / (vazão terminal - entrada única)`, apenas se a vazão terminal superar a entrada. Calcular por lane e incluir tentativas/retries; divisão agregada pode esconder atraso de uma lane.

O teto nominal de 180 TPS do legado não foi medido como capacidade do sistema completo. Os pisos 70/30 correspondem a 126/54 envios/s em condições ideais; sem entrada concorrente ou retries, 120 s × 126 = 15.120 envios fast e 2.700 s × 54 = 145.800 standard são apenas limites teóricos de backlog, não garantias deste ambiente.

No ambiente local, usar a vazão terminal observada, substancialmente inferior ao teto nominal. Os prazos J2 de 2 min/45 min permanecem: afrouxá-los esconderia a violação observada na saturação. Não houve evidência suficiente de inanição para justificar aging; ele não foi implementado.

## Reprodução

Após build completo verde, em applications/:

```powershell
.\scripts\run-capacity-local.ps1 -Seconds 15 -Rates '1,3,5,10,30,60,120,240'
.\scripts\run-capacity-local.ps1 -Seconds 60 -Rates '1,5'
```

O script não inicia sobre uma sessão registrada nem ocupa um Prometheus preexistente na porta 9090. Encerra os processos iniciados em finally. O relatório do gerador fica em `.local/capacity-*/report.json`; dados e snapshot do Prometheus do orquestrador em `.local/capacity-stack-*/`.
