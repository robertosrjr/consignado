package br.com.consignado.platform.localenv;

import br.com.consignado.platform.shared.contract.InboxAttributes;
import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.StreamSpecification;
import software.amazon.awssdk.services.dynamodb.model.StreamViewType;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveSpecification;
import software.amazon.awssdk.services.dynamodb.model.UpdateTimeToLiveRequest;

/**
 * DynamoDB Local embarcado na JVM (em memoria, com Streams), acessivel por HTTP no loopback. Sem
 * Docker. Os dados se perdem ao encerrar; e uma ferramenta de desenvolvimento e de testes.
 */
public final class LocalDynamoDb implements AutoCloseable {

  private final DynamoDBProxyServer server;
  private final int port;

  private LocalDynamoDb(DynamoDBProxyServer server, int port) {
    this.server = server;
    this.port = port;
  }

  /** Inicia na porta informada; {@code 0} escolhe uma porta livre. */
  public static LocalDynamoDb start(int requestedPort) throws Exception {
    int port = requestedPort == 0 ? freePort() : requestedPort;
    DynamoDBProxyServer server =
        ServerRunner.createServerFromCommandLineArgs(
            new String[] {"-inMemory", "-port", Integer.toString(port), "-disableTelemetry"});
    server.start();
    return new LocalDynamoDb(server, port);
  }

  public int port() {
    return port;
  }

  public String endpoint() {
    return "http://127.0.0.1:" + port;
  }

  /** Cliente apontando para este servidor (credenciais fficticias de teste). */
  public DynamoDbClient client() {
    return DynamoDbClient.builder()
        .endpointOverride(URI.create(endpoint()))
        .region(Region.SA_EAST_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .httpClient(UrlConnectionHttpClient.create())
        .build();
  }

  /**
   * Cria a tabela do DurableInbox com Streams (NEW_IMAGE), TTL no atributo {@code ttl} e o indice
   * esparso de prazos do SLO J2.
   */
  public void createInboxTable(DynamoDbClient client, String tableName) {
    client.createTable(
        CreateTableRequest.builder()
            .tableName(tableName)
            .attributeDefinitions(
                AttributeDefinition.builder()
                    .attributeName(InboxAttributes.IDEMPOTENCY_KEY)
                    .attributeType(ScalarAttributeType.S)
                    .build(),
                AttributeDefinition.builder()
                    .attributeName(InboxAttributes.SLO_OPEN)
                    .attributeType(ScalarAttributeType.S)
                    .build(),
                AttributeDefinition.builder()
                    .attributeName(InboxAttributes.DISPATCH_PENDING)
                    .attributeType(ScalarAttributeType.S)
                    .build(),
                AttributeDefinition.builder()
                    .attributeName(InboxAttributes.CREATED_AT)
                    .attributeType(ScalarAttributeType.N)
                    .build())
            .keySchema(
                KeySchemaElement.builder()
                    .attributeName(InboxAttributes.IDEMPOTENCY_KEY)
                    .keyType(KeyType.HASH)
                    .build())
            .globalSecondaryIndexes(
                GlobalSecondaryIndex.builder()
                    .indexName(InboxAttributes.SLO_OPEN_INDEX)
                    .keySchema(
                        KeySchemaElement.builder()
                            .attributeName(InboxAttributes.SLO_OPEN)
                            .keyType(KeyType.HASH)
                            .build(),
                        KeySchemaElement.builder()
                            .attributeName(InboxAttributes.CREATED_AT)
                            .keyType(KeyType.RANGE)
                            .build())
                    .projection(
                        Projection.builder()
                            .projectionType(ProjectionType.INCLUDE)
                            .nonKeyAttributes(InboxAttributes.DECISION, InboxAttributes.DECIDED_AT)
                            .build())
                    .build(),
                GlobalSecondaryIndex.builder()
                    .indexName(InboxAttributes.DISPATCH_PENDING_INDEX)
                    .keySchema(
                        KeySchemaElement.builder()
                            .attributeName(InboxAttributes.DISPATCH_PENDING)
                            .keyType(KeyType.HASH)
                            .build(),
                        KeySchemaElement.builder()
                            .attributeName(InboxAttributes.CREATED_AT)
                            .keyType(KeyType.RANGE)
                            .build())
                    .projection(
                        Projection.builder()
                            .projectionType(ProjectionType.INCLUDE)
                            .nonKeyAttributes(
                                InboxAttributes.TENANT_ID,
                                InboxAttributes.CONVENIO_TYPE,
                                InboxAttributes.PORTABILITY,
                                InboxAttributes.AMOUNT,
                                InboxAttributes.TRACING_ID,
                                InboxAttributes.STATUS)
                            .build())
                    .build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .streamSpecification(
                StreamSpecification.builder()
                    .streamEnabled(true)
                    .streamViewType(StreamViewType.NEW_IMAGE)
                    .build())
            .build());
    client.updateTimeToLive(
        UpdateTimeToLiveRequest.builder()
            .tableName(tableName)
            .timeToLiveSpecification(
                TimeToLiveSpecification.builder()
                    .enabled(true)
                    .attributeName(InboxAttributes.TTL)
                    .build())
            .build());
  }

  /**
   * Tabela do estado duravel do Worker: item {@code STATE} por proposta e itens {@code EVENT#...}
   * do historico, na mesma particao ({@code proposalKey}).
   */
  public void createWorkerProposalTable(DynamoDbClient client, String tableName) {
    client.createTable(
        CreateTableRequest.builder()
            .tableName(tableName)
            .attributeDefinitions(
                AttributeDefinition.builder()
                    .attributeName("proposalKey")
                    .attributeType(ScalarAttributeType.S)
                    .build(),
                AttributeDefinition.builder()
                    .attributeName("sk")
                    .attributeType(ScalarAttributeType.S)
                    .build())
            .keySchema(
                KeySchemaElement.builder()
                    .attributeName("proposalKey")
                    .keyType(KeyType.HASH)
                    .build(),
                KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build());
  }

  @Override
  public void close() throws Exception {
    server.stop();
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
