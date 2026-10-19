package br.com.consignado.platform.localenv;

import br.com.consignado.platform.dispatcher.config.DispatcherConfig;
import br.com.consignado.platform.shared.contract.InboxAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Sobe o ambiente local da PoC <b>sem Docker</b>, em uma unica JVM: DynamoDB Local (com a tabela
 * DurableInbox e Streams), ElasticMQ (filas fast, standard e DLQ), emissor JWT local e o executor
 * do Dispatcher. Imprime os endpoints e as variaveis de ambiente que os servicos devem usar.
 *
 * <p>Portas (variaveis opcionais): LOCAL_ENV_DYNAMODB_PORT (8000), LOCAL_ENV_SQS_PORT (9324),
 * LOCAL_ENV_TOKEN_PORT (8082). Apenas loopback; tudo em memoria.
 */
public final class LocalEnvMain {

  public static final String AUDIENCE = "consignado-ingestion";

  private LocalEnvMain() {}

  public static void main(String[] args) throws Exception {
    var telemetry =
        br.com.consignado.platform.telemetry.StandaloneTelemetry.create(
            "dispatcher-local", System.getenv());
    br.com.consignado.platform.telemetry.Telemetry.configure(telemetry);
    int dynamoPort = port("LOCAL_ENV_DYNAMODB_PORT", 8000);
    int sqsPort = port("LOCAL_ENV_SQS_PORT", 9324);
    int tokenPort = port("LOCAL_ENV_TOKEN_PORT", 8082);

    LocalDynamoDb dynamo = LocalDynamoDb.start(dynamoPort);
    DynamoDbClient dynamoClient = dynamo.client();
    dynamo.createInboxTable(dynamoClient, InboxAttributes.TABLE_NAME);

    LocalSqs sqs = LocalSqs.start(sqsPort);
    SqsClient sqsClient = sqs.client();
    LocalSqs.QueueUrls queues = sqs.createLaneQueues(sqsClient);

    LocalTokenIssuer tokens = LocalTokenIssuer.start(tokenPort, AUDIENCE);

    Map<String, String> dispatcherEnv = new HashMap<>(System.getenv());
    dispatcherEnv.put("DISPATCHER_FAST_QUEUE_URL", queues.fast());
    dispatcherEnv.put("DISPATCHER_STANDARD_QUEUE_URL", queues.standard());
    dispatcherEnv.put("DISPATCHER_INBOX_TABLE", InboxAttributes.TABLE_NAME);
    DispatcherConfig config = DispatcherConfig.fromEnvironment(dispatcherEnv);
    LocalStreamDispatcher dispatcher =
        new LocalStreamDispatcher(
            dynamo,
            dynamoClient,
            InboxAttributes.TABLE_NAME,
            config.batchDispatcher(sqsClient, dynamoClient));
    dispatcher.start(50);

    CountDownLatch stop = new CountDownLatch(1);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    dispatcher.close();
                    tokens.close();
                    sqs.close();
                    dynamo.close();
                  } catch (Exception e) {
                    // encerramento: ignora
                  } finally {
                    telemetry.close();
                    stop.countDown();
                  }
                }));

    System.out.println("LOCAL-ENV PRONTO (sem Docker, tudo em memoria, somente loopback)");
    System.out.println("  DynamoDB Local : " + dynamo.endpoint());
    System.out.println("  SQS (ElasticMQ): " + sqs.endpoint());
    System.out.println(
        "  Emissor JWT    : " + tokens.issuer() + "  (JWKS: " + tokens.jwksUri() + ")");
    System.out.println("  Fila fast      : " + queues.fast());
    System.out.println("  Fila standard  : " + queues.standard());
    System.out.println("  DLQ            : " + queues.dlq());
    System.out.println("  Token exemplo  : GET " + tokens.issuer() + "/token?tenant=banco-a");
    stop.await();
  }

  private static int port(String variable, int fallback) {
    String value = System.getenv(variable);
    return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
  }
}
