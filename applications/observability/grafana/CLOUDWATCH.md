# CloudWatch como fonte de dados do Grafana

O Prometheus cobre a aplicacao (ingestion, worker, mock-legacy). O que roda como servico gerenciado
fica no CloudWatch e entra no mesmo Grafana pela fonte `CloudWatch`
(`provisioning/datasources/datasources.yml`, regiao `sa-east-1`).

## Credenciais
Sem chaves em arquivo: o Grafana usa a role do EC2 de observabilidade (`authType: default`). Anexe a
essa role a politica `cloudwatch-iam-policy.json` (somente leitura). Local (sem AWS) a fonte aparece
sem dados; nao e erro.

## Metricas para os paineis

| Painel | Namespace / metrica | Dimensao | Estatistica |
|---|---|---|---|
| Profundidade da fila | `AWS/SQS` `ApproximateNumberOfMessagesVisible` | `QueueName` (fast, standard) | Maximum |
| Idade da mais antiga | `AWS/SQS` `ApproximateAgeOfOldestMessage` | `QueueName` | Maximum |
| DLQ | `AWS/SQS` `ApproximateNumberOfMessagesVisible` | `QueueName=sqs-dlq` | Maximum |
| Despacho por lane | `ConsignadoPoc/Dispatcher` `RecordsDispatched` | `Lane` | Sum |
| Falhas do Dispatcher | `ConsignadoPoc/Dispatcher` `RecordsFailed`, `RecordsSkipped` | (nenhuma) | Sum |
| Lambda | `AWS/Lambda` `Errors`, `Duration`, `Throttles`, `IteratorAge` | `FunctionName` | Sum / p95 |
| DynamoDB | `AWS/DynamoDB` `ConsumedWriteCapacityUnits`, `ThrottledRequests` | `TableName=DurableInbox` | Sum |

`IteratorAge` (Lambda) mostra atraso do stream do DynamoDB para o Dispatcher.

## Alertas
Alarme de CloudWatch (nao regra do Prometheus) para a DLQ:
`ApproximateNumberOfMessagesVisible > 0` por 1 periodo de 60 s, na fila `sqs-dlq`.

## Limites a considerar
- Resolucao padrao de 1 min nas metricas AWS: nao mostra o pico de 10.000 TPS segundo a segundo.
  Para isso valem as metricas de aplicacao no Prometheus.
- `GetMetricData` e cobrado por metrica consultada; manter o refresh do painel em 1 min ou mais.
