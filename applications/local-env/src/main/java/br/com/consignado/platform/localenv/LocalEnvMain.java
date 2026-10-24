package br.com.consignado.platform.localenv;

import br.com.consignado.platform.dispatcher.config.DispatcherConfig;
import br.com.consignado.platform.dispatcher.dispatch.OrphanSweeper;
import br.com.consignado.platform.localenv.incident.AlertWebhookBridge;
import br.com.consignado.platform.localenv.incident.FileIncidentNotifier;
import br.com.consignado.platform.localenv.incident.IncidentProcessor;
import br.com.consignado.platform.localenv.incident.IncidentQueueConsumer;
import br.com.consignado.platform.shared.contract.InboxAttributes;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
  public static final String WORKER_PROPOSAL_TABLE = "WorkerProposal";

  private LocalEnvMain() {}

  public static void main(String[] args) throws Exception {
    var telemetry =
        br.com.consignado.platform.telemetry.StandaloneTelemetry.create(
            "dispatcher-local", System.getenv());
    br.com.consignado.platform.telemetry.Telemetry.configure(telemetry);
    int dynamoPort = port("LOCAL_ENV_DYNAMODB_PORT", 8000);
    int sqsPort = port("LOCAL_ENV_SQS_PORT", 9324);
    int tokenPort = port("LOCAL_ENV_TOKEN_PORT", 8082);
    int alertPort = port("LOCAL_ENV_ALERT_WEBHOOK_PORT", 9096);

    LocalDynamoDb dynamo = LocalDynamoDb.start(dynamoPort);
    DynamoDbClient dynamoClient = dynamo.client();
    dynamo.createInboxTable(dynamoClient, InboxAttributes.TABLE_NAME);
    dynamo.createWorkerProposalTable(dynamoClient, WORKER_PROPOSAL_TABLE);

    LocalSqs sqs = LocalSqs.start(sqsPort);
    SqsClient sqsClient = sqs.client();
    LocalSqs.QueueUrls queues = sqs.createLaneQueues(sqsClient);

    LocalTokenIssuer tokens = LocalTokenIssuer.start(tokenPort, AUDIENCE);

    // Alertmanager -> ponte webhook -> sqs-alerts -> consumidor -> registro de incidentes
    String alertQueue = sqs.createAlertQueue(sqsClient);
    String incidentsEnv = System.getenv("LOCAL_ENV_INCIDENTS_FILE");
    Path incidentsFile =
        Path.of(
            incidentsEnv == null || incidentsEnv.isBlank()
                ? ".local/incidents/incidents.jsonl"
                : incidentsEnv);
    FileIncidentNotifier incidentLog = new FileIncidentNotifier(incidentsFile);
    IncidentQueueConsumer incidents =
        new IncidentQueueConsumer(
            sqsClient,
            alertQueue,
            new IncidentProcessor(incidentLog, Clock.systemUTC(), incidentLog.history()));
    incidents.start();
    AlertWebhookBridge alertBridge = AlertWebhookBridge.start(alertPort, sqsClient, alertQueue);

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

    // Reconciliacao de orfaos (RECEIVED sem despacho apos 60 s), a cada 30 s
    OrphanSweeper sweeper =
        config.orphanSweeper(
            sqsClient, dynamoClient, Clock.systemUTC(), Duration.ofSeconds(60), 500);
    ScheduledExecutorService sweeps =
        Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
    sweeps.scheduleWithFixedDelay(
        () -> {
          try {
            sweeper.sweep();
          } catch (RuntimeException e) {
            // proxima varredura tenta de novo
          }
        },
        30,
        30,
        TimeUnit.SECONDS);

    CountDownLatch stop = new CountDownLatch(1);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    sweeps.shutdownNow();
                    dispatcher.close();
                    alertBridge.close();
                    incidents.close();
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
    System.out.println("  Webhook alertas: " + alertBridge.url() + "  -> " + alertQueue);
    System.out.println("  Incidentes     : " + incidentsFile.toAbsolutePath());
    System.out.println("  Token exemplo  : GET " + tokens.issuer() + "/token?tenant=banco-a");
    stop.await();
  }

  private static int port(String variable, int fallback) {
    String value = System.getenv(variable);
    return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
  }
}
