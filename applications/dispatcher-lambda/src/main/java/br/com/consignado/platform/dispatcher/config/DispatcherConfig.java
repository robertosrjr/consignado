package br.com.consignado.platform.dispatcher.config;

import br.com.consignado.platform.dispatcher.aws.DynamoInboxStatusUpdater;
import br.com.consignado.platform.dispatcher.aws.DynamoPendingDispatchReader;
import br.com.consignado.platform.dispatcher.aws.SqsQueuePublisher;
import br.com.consignado.platform.dispatcher.dispatch.BatchDispatcher;
import br.com.consignado.platform.dispatcher.dispatch.DispatchService;
import br.com.consignado.platform.dispatcher.dispatch.OrphanSweeper;
import br.com.consignado.platform.dispatcher.routing.LaneRouter;
import br.com.consignado.platform.dispatcher.routing.PriorityRules;
import br.com.consignado.platform.dispatcher.routing.PriorityScorer;
import br.com.consignado.platform.shared.vo.Money;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Configuracao do Dispatcher por variaveis de ambiente (DISPATCHER_*). Os clientes AWS usam a
 * cadeia padrao de credenciais e as variaveis AWS_REGION / AWS_ENDPOINT_URL_*.
 */
public final class DispatcherConfig {

  static final String FAST_QUEUE_URL = "DISPATCHER_FAST_QUEUE_URL";
  static final String STANDARD_QUEUE_URL = "DISPATCHER_STANDARD_QUEUE_URL";
  static final String INBOX_TABLE = "DISPATCHER_INBOX_TABLE";
  static final String VIP_TENANTS = "DISPATCHER_VIP_TENANTS";
  static final String AMOUNT_THRESHOLD = "DISPATCHER_AMOUNT_THRESHOLD";
  static final String FAST_THRESHOLD = "DISPATCHER_FAST_THRESHOLD";

  private final String fastQueueUrl;
  private final String standardQueueUrl;
  private final String inboxTable;
  private final PriorityRules rules;

  private DispatcherConfig(
      String fastQueueUrl, String standardQueueUrl, String inboxTable, PriorityRules rules) {
    this.fastQueueUrl = fastQueueUrl;
    this.standardQueueUrl = standardQueueUrl;
    this.inboxTable = inboxTable;
    this.rules = rules;
  }

  public static DispatcherConfig fromEnvironment(Map<String, String> env) {
    String fast = required(env, FAST_QUEUE_URL);
    String standard = required(env, STANDARD_QUEUE_URL);
    String table = env.getOrDefault(INBOX_TABLE, "DurableInbox");
    Set<String> vip = parseCsv(env.getOrDefault(VIP_TENANTS, ""));
    Money threshold = Money.of(env.getOrDefault(AMOUNT_THRESHOLD, "50000.00"));
    int fastThreshold =
        Integer.parseInt(
            env.getOrDefault(
                FAST_THRESHOLD, Integer.toString(PriorityRules.DEFAULT_FAST_THRESHOLD)));
    PriorityRules rules =
        new PriorityRules(
            vip,
            threshold,
            PriorityRules.DEFAULT_PORTABILITY_WEIGHT,
            PriorityRules.DEFAULT_VIP_WEIGHT,
            PriorityRules.DEFAULT_AMOUNT_WEIGHT,
            fastThreshold);
    return new DispatcherConfig(fast, standard, table, rules);
  }

  public PriorityRules rules() {
    return rules;
  }

  public String fastQueueUrl() {
    return fastQueueUrl;
  }

  public String standardQueueUrl() {
    return standardQueueUrl;
  }

  public String inboxTable() {
    return inboxTable;
  }

  /** Monta o despachador de lotes com os clientes AWS informados. */
  public BatchDispatcher batchDispatcher(SqsClient sqs, DynamoDbClient dynamo) {
    return new BatchDispatcher(dispatchService(sqs, dynamo));
  }

  /** Varredura de orfaos com o mesmo despacho do Stream (carencia evita corrida com o Stream). */
  public OrphanSweeper orphanSweeper(
      SqsClient sqs, DynamoDbClient dynamo, Clock clock, Duration grace, int batchSize) {
    return new OrphanSweeper(
        new DynamoPendingDispatchReader(dynamo, inboxTable),
        dispatchService(sqs, dynamo),
        clock,
        grace,
        batchSize);
  }

  private DispatchService dispatchService(SqsClient sqs, DynamoDbClient dynamo) {
    return new DispatchService(
        new PriorityScorer(rules),
        new LaneRouter(rules.fastThreshold()),
        new SqsQueuePublisher(sqs, fastQueueUrl, standardQueueUrl),
        new DynamoInboxStatusUpdater(dynamo, inboxTable));
  }

  private static String required(Map<String, String> env, String name) {
    String value = env.get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Variavel obrigatoria ausente: " + name);
    }
    return value;
  }

  private static Set<String> parseCsv(String csv) {
    Set<String> values = new LinkedHashSet<>();
    Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(item -> !item.isEmpty())
        .forEach(values::add);
    return values;
  }
}
