package br.com.consignado.platform.localenv;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.Map;
import org.elasticmq.NodeAddress;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * ElasticMQ (API compativel com SQS) embarcado na JVM, no loopback e em memoria. Sem Docker. Cria
 * as filas fast, standard e DLQ com redrive apos 3 recebimentos, como na AWS.
 */
public final class LocalSqs implements AutoCloseable {

  public static final String FAST_QUEUE = "sqs-fast-lane-queue";
  public static final String STANDARD_QUEUE = "sqs-standard-lane-queue";
  public static final String DLQ = "sqs-dlq";

  /** URLs das filas criadas. */
  public record QueueUrls(String fast, String standard, String dlq) {}

  private final SQSRestServer server;
  private final int port;

  private LocalSqs(SQSRestServer server, int port) {
    this.server = server;
    this.port = port;
  }

  /** Inicia na porta informada; {@code 0} escolhe uma porta livre. */
  public static LocalSqs start(int requestedPort) throws IOException {
    int port = requestedPort == 0 ? freePort() : requestedPort;
    SQSRestServer server =
        SQSRestServerBuilder.withPort(port)
            .withInterface("127.0.0.1")
            .withServerAddress(new NodeAddress("http", "127.0.0.1", port, ""))
            .start();
    server.waitUntilStarted();
    return new LocalSqs(server, port);
  }

  public int port() {
    return port;
  }

  public String endpoint() {
    return "http://127.0.0.1:" + port;
  }

  /** Cliente apontando para este servidor (credenciais ficticias de teste). */
  public SqsClient client() {
    return SqsClient.builder()
        .endpointOverride(URI.create(endpoint()))
        .region(Region.SA_EAST_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .httpClient(UrlConnectionHttpClient.create())
        .build();
  }

  /** Cria a DLQ e as filas das lanes com redrive (maxReceiveCount = 3). */
  public QueueUrls createLaneQueues(SqsClient client) {
    String dlqUrl =
        client.createQueue(CreateQueueRequest.builder().queueName(DLQ).build()).queueUrl();
    String dlqArn =
        client
            .getQueueAttributes(
                GetQueueAttributesRequest.builder()
                    .queueUrl(dlqUrl)
                    .attributeNames(QueueAttributeName.QUEUE_ARN)
                    .build())
            .attributes()
            .get(QueueAttributeName.QUEUE_ARN);
    String redrive = "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"3\"}";
    Map<QueueAttributeName, String> attributes =
        Map.of(
            QueueAttributeName.REDRIVE_POLICY,
            redrive,
            QueueAttributeName.VISIBILITY_TIMEOUT,
            "30");
    String fast =
        client
            .createQueue(
                CreateQueueRequest.builder().queueName(FAST_QUEUE).attributes(attributes).build())
            .queueUrl();
    String standard =
        client
            .createQueue(
                CreateQueueRequest.builder()
                    .queueName(STANDARD_QUEUE)
                    .attributes(attributes)
                    .build())
            .queueUrl();
    return new QueueUrls(fast, standard, dlqUrl);
  }

  @Override
  public void close() {
    server.stopAndWait();
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
