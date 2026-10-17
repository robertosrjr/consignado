# Ambiente local de desenvolvimento

Pré-requisitos: JDK 25 e Maven 3.9+. As aplicações e emuladores rodam em processos Java; não exigem Docker local. A stack de observabilidade roda separadamente na EC2.

Na raiz do repositório:

```powershell
cd applications
mvn install -DskipTests
.\scripts\local-up.ps1
```

## Serviços locais

O módulo `local-env` substitui a AWS na máquina de desenvolvimento. Ele usa DynamoDB Local (AWS) e ElasticMQ (compatível com SQS), e não mocks de adaptadores, então a escrita condicional, o Stream e o redrive para a DLQ funcionam como na nuvem:

| No lugar de | `local-env` sobe | Porta |
|---|---|---|
| DynamoDB + Streams | DynamoDB Local, com a tabela `DurableInbox` e Streams habilitado | 8000 |
| SQS | ElasticMQ, com as filas fast, standard e DLQ (redrive após 3 tentativas) | 9324 |
| Provedor de identidade | Emissor de JWT local: JWKS e `GET /token?tenant=<id>` | 8082 |
| Gatilho Stream → Lambda | `LocalStreamDispatcher`: lê o stream e chama o **mesmo** `BatchDispatcher` do `dispatcher-lambda` | — |

As aplicações não sabem que estão rodando localmente: `ingestion-api` e `worker` usam os adaptadores AWS de produção, apontados para esses endpoints. O `scripts\local-up.ps1` sobe o `local-env` e depois o `mock-legacy` (8081), a `ingestion-api` (8080) e o `worker` (8083). O script também gera os segredos locais (chave do payload e API key do legado) e os repassa a cada processo. `scripts\local-down.ps1` encerra tudo.

Fica de fora do local: KMS (o payload usa chave AES local), CloudWatch/EMF e a stack de observabilidade.

## Dados e métricas

DynamoDB Local e ElasticMQ rodam em memória: reiniciar perde propostas e mensagens. Os estados do worker e o ledger do legado também são locais à execução. Os segredos gerados são repassados às aplicações; não são gravados em env.json.

Para enviar métricas, iniciar com o endereço atual do Pushgateway:

```powershell
.\scripts\local-up.ps1 -PushgatewayAddress '<IP_PUBLICO_ATUAL>:9091'
```

O script reutiliza o último endereço salvo; conferir após stop/start da EC2, pois o IP pode mudar. Para encerrar, executar .\scripts\local-down.ps1 a partir de applications.

Detalhes: [guia das aplicações](../../applications/README.md), [local-env](../../applications/local-env/README.md) e [dashboards](../../applications/observability/grafana/dashboards/README.md).
