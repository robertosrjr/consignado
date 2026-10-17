# Infraestrutura da PoC

Revisão: 16/10/2026. A stack de observabilidade roda em Docker Compose na EC2; as aplicações de teste podem rodar como processos Java na estação local.

## Stack de observabilidade

- Variante usada na EC2: [compose.sem-elasticsearch.yaml](Configuration/observability/compose.sem-elasticsearch.yaml).
- Serviços: Prometheus, Pushgateway, Grafana, Alertmanager e Jaeger com armazenamento Badger.
- [Regras SRE e notificações](Configuration/observability/ALERTAS.md): Alertmanager → SNS → SQS. A rota legada de webhook permanece separada.
- [Diagnóstico de tracing](Configuration/observability/TRACING_TROUBLESHOOTING.md): backend preparado; tracing completo da aplicação pendente.
- [Dashboards versionados](../applications/observability/grafana/dashboards/README.md).

A variante [compose.yaml](Configuration/observability/compose.yaml) inclui Elasticsearch e não deve ser confundida com a variante ativa sem Elasticsearch. Na EC2, informar explicitamente o arquivo de Compose.

```sh
cd /home/ubuntu/observability
docker compose -f compose.sem-elasticsearch.yaml config --quiet
docker compose -f compose.sem-elasticsearch.yaml up -d
docker compose -f compose.sem-elasticsearch.yaml ps
```

`stop` preserva os volumes. `down -v` apaga os dados persistidos: não usar como rotina de desligamento. O IP público pode mudar após stop/start; consultar o endereço atual antes de conectar e atualizar os endpoints locais conforme necessário.

## Terraform e adoção de recursos

- [Alertas SNS/SQS e IAM da EC2](environments/observability-alerting/README.md): configuração para importar/adotar recursos existentes; não houve apply nesta sessão.
- [Business Fingerprint](modules/business-fingerprint/main.tf): tabela e KMS para o modo opt-in; recursos ainda não provisionados.

A configuração de um recurso em Terraform não demonstra que ele já existe na conta. Revisar inventário, importações e plano antes de aplicar; não gerenciar o mesmo recurso em dois estados.

## Obstáculos e pendências

Tracing distribuído, validação operacional do datasource CloudWatch e conclusão da adoção pelo Terraform continuam pendentes. Segredos ficam fora do Git; não incluir senhas de Grafana ou chaves AWS na documentação.
