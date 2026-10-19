# local-env

Ferramenta offline adicional: [relatório local SRE](../../docs/sre/relatorio-local.md), com contrato de medição, fixtures e saída JSON/Markdown. A CLI não inicia os emuladores ou a aplicação.

Ferramenta de desenvolvimento. Sobe, **em uma única JVM e sem Docker**, tudo de que a PoC depende: DynamoDB Local, uma fila compatível com SQS, um emissor de JWT e o acionador do Dispatcher.

> **Não é implantado.** É dependência de desenvolvimento e de teste de integração; nenhum ambiente real usa este módulo.

## Por que existe

A máquina de desenvolvimento não tem Docker, então Testcontainers e `docker compose` estão fora. Sem um substituto, só restaria mockar DynamoDB e SQS — e aí os testes provariam apenas que os mocks concordam com o código, não que a idempotência funciona no banco real.

Este módulo usa os emuladores **oficiais** embarcados na JVM: `DynamoDBLocal` (o mesmo motor da AWS, com Streams) e `ElasticMQ` (API do SQS). A escrita condicional e o redrive são exercitados de verdade.

## O que ele sobe

| Componente | Porta | Papel |
|---|---|---|
| DynamoDB Local | 8000 | tabela `DurableInbox` com Streams |
| ElasticMQ (SQS) | 9324 | filas fast, standard e DLQ (redrive após 3 tentativas) |
| Emissor JWT | 8082 | JWKS e `GET /token?tenant=<id>` |
| `LocalStreamDispatcher` | — | substitui o acionamento da Lambda |

Tudo em memória e **apenas em loopback**. Ao encerrar, nada persiste.

## O substituto da Lambda

Na AWS, o DynamoDB Streams aciona a Lambda automaticamente. Localmente isso não existe, então o `LocalStreamDispatcher` faz polling do stream e chama o **mesmo** `BatchDispatcher` do módulo `dispatcher-lambda`. A lógica de roteamento testada é a de produção; só o gatilho é diferente.

## Como rodar

```bash
mvn -pl local-env exec:java
```

Ele imprime os endpoints e as URLs das filas que os outros serviços devem usar. Para o conjunto completo, prefira `../scripts/local-up.ps1`, que também gera e propaga os segredos.

Exige `mvn install -DskipTests` antes, para os módulos irmãos estarem no repositório local.

## Bibliotecas nativas

O DynamoDB Local usa SQLite via JNI. O build copia as bibliotecas nativas para `target/native-libs`, e a propriedade `sqlite4java.library.path` aponta para lá — já configurada no `exec:java` e no Failsafe. Se aparecer erro de carregamento de biblioteca nativa, rode `mvn -pl local-env package` para repopular essa pasta.

## Credenciais

Os emuladores exigem credenciais quaisquer para o SDK funcionar; usa-se `test`/`test`. É exceção documentada de ambiente local — **nunca** aponte este módulo para a AWS real.

## Testes

Para reconciliar um experimento JMeter com a Inbox local e o snapshot do worker, consultar a
[captura SRE local](../../docs/sre/captura-local.md). O coletor usa somente o DynamoDB em
`127.0.0.1:8000`, valida a sessão dos processos e gera relatório sem publicação remota.

```bash
mvn -pl local-env verify -Pintegration
```
