package br.com.consignado.platform.dispatcher.lambda;

import br.com.consignado.platform.dispatcher.config.DispatcherConfig;
import br.com.consignado.platform.dispatcher.dispatch.BatchDispatcher;
import br.com.consignado.platform.dispatcher.dispatch.BatchResult;
import br.com.consignado.platform.dispatcher.dispatch.StreamRecordView;
import br.com.consignado.platform.dispatcher.metrics.EmfMetrics;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse;
import java.time.Clock;
import java.util.List;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Entrada do AWS Lambda (handler {@code ...lambda.InboxStreamHandler::handleRequest}). Falhas
 * parciais voltam como {@code batchItemFailures}, de modo que o Stream repete somente o necessario.
 */
public class InboxStreamHandler implements RequestHandler<DynamodbEvent, StreamsEventResponse> {

  private final BatchDispatcher dispatcher;
  private final EmfMetrics metrics;
  private boolean runtimeTracing;

  private static class RuntimeTelemetry {
    static final io.opentelemetry.sdk.OpenTelemetrySdk SDK = initialize();

    private static io.opentelemetry.sdk.OpenTelemetrySdk initialize() {
      var sdk =
          br.com.consignado.platform.telemetry.StandaloneTelemetry.create(
              "dispatcher-lambda", System.getenv());
      br.com.consignado.platform.telemetry.Telemetry.configure(sdk);
      Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));
      return sdk;
    }
  }

  /** Construtor usado pelo runtime do Lambda: configuracao por variaveis de ambiente. */
  public InboxStreamHandler() {
    this(
        DispatcherConfig.fromEnvironment(System.getenv())
            .batchDispatcher(SqsClient.create(), DynamoDbClient.create()),
        new EmfMetrics(System.out, Clock.systemUTC()));
    RuntimeTelemetry.SDK.getSdkTracerProvider();
    runtimeTracing = true;
  }

  /** Construtor para testes e para o executor local. */
  public InboxStreamHandler(BatchDispatcher dispatcher, EmfMetrics metrics) {
    this.dispatcher = dispatcher;
    this.metrics = metrics;
  }

  @Override
  public StreamsEventResponse handleRequest(DynamodbEvent event, Context context) {
    try {
      return dispatch(event);
    } finally {
      if (runtimeTracing) {
        RuntimeTelemetry.SDK
            .getSdkTracerProvider()
            .forceFlush()
            .join(1, java.util.concurrent.TimeUnit.SECONDS);
      }
    }
  }

  private StreamsEventResponse dispatch(DynamodbEvent event) {
    List<StreamRecordView> records =
        event.getRecords().stream().map(StreamRecordMapper::toView).toList();
    BatchResult result = dispatcher.dispatch(records);
    metrics.publish(result);

    List<StreamsEventResponse.BatchItemFailure> failures =
        result.failedSequenceNumbers().stream()
            .map(seq -> new StreamsEventResponse.BatchItemFailure(seq))
            .toList();
    return new StreamsEventResponse(failures);
  }
}
