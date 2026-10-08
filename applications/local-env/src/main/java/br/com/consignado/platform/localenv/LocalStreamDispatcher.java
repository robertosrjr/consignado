package br.com.consignado.platform.localenv;

import br.com.consignado.platform.dispatcher.dispatch.BatchDispatcher;
import br.com.consignado.platform.dispatcher.dispatch.BatchResult;
import br.com.consignado.platform.dispatcher.dispatch.StreamRecordView;
import br.com.consignado.platform.shared.contract.InboxAttributes;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Record;
import software.amazon.awssdk.services.dynamodb.model.Shard;
import software.amazon.awssdk.services.dynamodb.model.ShardIteratorType;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

/**
 * Executor local do Dispatcher (substitui o event source mapping do Lambda, que nao existe sem
 * Docker/AWS): le o DynamoDB Stream do DynamoDB Local e invoca o <b>mesmo</b> {@link
 * BatchDispatcher} do Lambda. Registros com falha sao repetidos algumas vezes (como a politica de
 * retentativas do Stream). Somente para desenvolvimento e testes.
 */
public final class LocalStreamDispatcher implements AutoCloseable {

  private static final Logger LOG = System.getLogger(LocalStreamDispatcher.class.getName());
  private static final int MAX_ATTEMPTS = 3;

  private final DynamoDbStreamsClient streams;
  private final String streamArn;
  private final BatchDispatcher dispatcher;
  private final Map<String, String> iterators = new HashMap<>();
  private volatile boolean running;
  private Thread thread;

  public LocalStreamDispatcher(
      LocalDynamoDb dynamo, DynamoDbClient client, String tableName, BatchDispatcher dispatcher) {
    this.streams =
        DynamoDbStreamsClient.builder()
            .endpointOverride(URI.create(dynamo.endpoint()))
            .region(Region.SA_EAST_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
            .httpClient(UrlConnectionHttpClient.create())
            .build();
    this.streamArn = client.describeTable(b -> b.tableName(tableName)).table().latestStreamArn();
    this.dispatcher = dispatcher;
  }

  /** Le o que houver no Stream agora e despacha; retorna a quantidade de registros lidos. */
  public int pollOnce() {
    int read = 0;
    List<Shard> shards =
        streams.describeStream(b -> b.streamArn(streamArn)).streamDescription().shards();
    for (Shard shard : shards) {
      String iterator =
          iterators.computeIfAbsent(
              shard.shardId(),
              id ->
                  streams
                      .getShardIterator(
                          b ->
                              b.streamArn(streamArn)
                                  .shardId(id)
                                  .shardIteratorType(ShardIteratorType.TRIM_HORIZON))
                      .shardIterator());
      if (iterator == null) {
        continue;
      }
      var response = streams.getRecords(b -> b.shardIterator(iterator).limit(100));
      List<StreamRecordView> views = new ArrayList<>();
      for (Record record : response.records()) {
        views.add(toView(record));
      }
      if (!views.isEmpty()) {
        dispatchWithRetries(views);
        read += views.size();
      }
      iterators.put(shard.shardId(), response.nextShardIterator());
    }
    return read;
  }

  private void dispatchWithRetries(List<StreamRecordView> batch) {
    List<StreamRecordView> pending = batch;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS && !pending.isEmpty(); attempt++) {
      BatchResult result = dispatcher.dispatch(pending);
      if (result.failed() == 0) {
        return;
      }
      List<StreamRecordView> failed = new ArrayList<>();
      for (StreamRecordView view : pending) {
        if (result.failedSequenceNumbers().contains(view.sequenceNumber())) {
          failed.add(view);
        }
      }
      pending = failed;
      if (attempt == MAX_ATTEMPTS) {
        LOG.log(Level.ERROR, "event=local_dispatch_gave_up records={0}", failed.size());
      } else {
        pause(200);
      }
    }
  }

  /** Inicia o laco de leitura em uma thread propria. */
  public synchronized void start(long pollMillis) {
    if (running) {
      return;
    }
    running = true;
    thread =
        Thread.ofPlatform()
            .name("local-stream-dispatcher")
            .daemon(true)
            .start(
                () -> {
                  while (running) {
                    try {
                      if (pollOnce() == 0) {
                        pause(pollMillis);
                      }
                    } catch (RuntimeException e) {
                      LOG.log(
                          Level.WARNING,
                          "event=local_stream_poll_failed error={0}",
                          e.getClass().getSimpleName());
                      pause(1_000);
                    }
                  }
                });
  }

  @Override
  public synchronized void close() {
    running = false;
    if (thread != null) {
      thread.interrupt();
    }
    streams.close();
  }

  private static StreamRecordView toView(Record record) {
    var dynamodb = record.dynamodb();
    Map<String, AttributeValue> image = dynamodb == null ? null : dynamodb.newImage();
    String sequence = dynamodb == null ? null : dynamodb.sequenceNumber();
    if (image == null || image.isEmpty()) {
      return new StreamRecordView(
          record.eventNameAsString(), sequence, null, null, null, null, null, null, null);
    }
    return new StreamRecordView(
        record.eventNameAsString(),
        sequence,
        s(image, InboxAttributes.IDEMPOTENCY_KEY),
        s(image, InboxAttributes.TENANT_ID),
        s(image, InboxAttributes.CONVENIO_TYPE),
        bool(image, InboxAttributes.PORTABILITY),
        n(image, InboxAttributes.AMOUNT),
        s(image, InboxAttributes.TRACING_ID),
        s(image, InboxAttributes.STATUS));
  }

  private static String s(Map<String, AttributeValue> image, String name) {
    AttributeValue value = image.get(name);
    return value == null ? null : value.s();
  }

  private static String n(Map<String, AttributeValue> image, String name) {
    AttributeValue value = image.get(name);
    return value == null ? null : value.n();
  }

  private static Boolean bool(Map<String, AttributeValue> image, String name) {
    AttributeValue value = image.get(name);
    return value == null ? null : value.bool();
  }

  private static void pause(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
