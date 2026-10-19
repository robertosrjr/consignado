package br.com.consignado.platform.localenv.sre;

import br.com.consignado.platform.shared.contract.InboxAttributes;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Read-only collector, restricted to a local DynamoDB endpoint and local process evidence. */
public final class SreCaptureMain {
  private static final JsonMapper JSON =
      JsonMapper.builder()
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .build();

  private SreCaptureMain() {}

  public record Control(
      int schemaVersion,
      String jtlFile,
      String jtlSha256,
      String summaryFile,
      String summarySha256,
      String startedAt,
      String endedAt,
      boolean clientComplete,
      long expectedAttempts,
      String workerInstanceId,
      long localEnvPid,
      String localEnvStartedAt,
      boolean fingerprintEnabled) {}

  public record Policy(
      int schemaVersion,
      String policyVersion,
      SreReportMain.Deadlines deadlineSeconds,
      SreReportMain.Targets targets) {}

  public record ClientSummary(int schemaVersion, long attempts, boolean completed) {}

  public static void main(String[] args) {
    int code = run(args);
    System.out.println("SRE capture exit=" + code + " (2=incomplete; no remote publication)");
    System.exit(code);
  }

  public static int run(String[] args) {
    if (args.length != 4) return 2;
    Path output;
    try {
      output = Path.of(args[3]);
      Files.createDirectory(output);
    } catch (Exception unavailable) {
      return 2;
    }
    try {
      var control = JSON.readValue(Files.readAllBytes(Path.of(args[0])), Control.class);
      var policy = JSON.readValue(Files.readAllBytes(Path.of(args[1])), Policy.class);
      if (control.schemaVersion() != 1 || policy.schemaVersion() != 1)
        throw new IllegalArgumentException("Invalid schema");
      byte[] jtl = Files.readAllBytes(Path.of(control.jtlFile()));
      String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jtl));
      if (!hash.equalsIgnoreCase(control.jtlSha256()))
        throw new IllegalArgumentException("JTL changed after completion");
      byte[] summaryBytes = Files.readAllBytes(Path.of(control.summaryFile()));
      if (!HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(summaryBytes))
          .equalsIgnoreCase(control.summarySha256())) {
        throw new IllegalArgumentException("Client summary changed after completion");
      }
      ClientSummary summary = JSON.readValue(summaryBytes, ClientSummary.class);
      List<JtlCapture.Sample> samples =
          JtlCapture.read(new String(jtl, java.nio.charset.StandardCharsets.UTF_8));
      if (samples.size() > 10000)
        throw new IllegalArgumentException("Local capture limited to 10000 proposal attempts");
      Instant start = Instant.parse(control.startedAt()), end = Instant.parse(control.endedAt());
      boolean sessionValid = sameProcess(control.localEnvPid(), control.localEnvStartedAt());
      try (var dynamo = localDynamo()) {
        // SDK startup may exceed the freshness budget: read the cut after initialization.
        var snapshot = snapshot(Path.of(args[2]), end);
        var normalized =
            SreCapture.normalize(
                samples,
                snapshot,
                control.workerInstanceId(),
                start,
                end,
                Instant.now(),
                control.clientComplete()
                    && summary.schemaVersion() == 1
                    && summary.completed()
                    && summary.attempts() == control.expectedAttempts()
                    && samples.size() == control.expectedAttempts(),
                sessionValid,
                sample ->
                    readInbox(
                        dynamo,
                        sample,
                        control.fingerprintEnabled(),
                        Instant.parse(snapshot.observedAt())));
        if (!sameProcess(control.localEnvPid(), control.localEnvStartedAt())) {
          var input = normalized.input();
          normalized =
              new SreCapture.Normalized(
                  new SreReportMain.Input(
                      1,
                      new SreReportMain.Coverage(
                          input.coverage().client(), false, input.coverage().decisions()),
                      input.attempts(),
                      input.acceptances(),
                      input.decisions()),
                  List.of("inbox_session_changed_during_collection"));
        }
        // A completed observation cut, rather than a caller-supplied future timestamp.
        Instant cut = Instant.parse(snapshot.observedAt());
        if (cut.isBefore(end)) cut = end;
        var manifest =
            new SreReportMain.Manifest(
                1,
                policy.policyVersion(),
                start.toString(),
                end.toString(),
                cut.toString(),
                control.expectedAttempts(),
                policy.deadlineSeconds(),
                policy.targets());
        write(output.resolve("manifest.json"), manifest);
        write(output.resolve("evidence.json"), normalized.input());
        write(
            output.resolve("capture.json"),
            Map.of(
                "schemaVersion",
                1,
                "sourceJtlSha256",
                hash,
                "workerInstanceId",
                snapshot.instanceId(),
                "workerObservedAt",
                snapshot.observedAt(),
                "issues",
                normalized.issues()));
      }
      return SreReportMain.run(
          new String[] {
            output.resolve("manifest.json").toString(),
            output.resolve("evidence.json").toString(),
            output.resolve("report").toString()
          });
    } catch (Exception unavailable) {
      try {
        write(
            output.resolve("capture-error.json"),
            Map.of(
                "schemaVersion",
                1,
                "status",
                "INCOMPLETE",
                "issues",
                List.of("capture_source_unavailable_changed_or_invalid")));
      } catch (Exception ignored) {
        /* Nonzero exit is authoritative if disk output is unavailable. */
      }
      // Reuse the report failure path: never manufacture a complete empty source.
      return SreReportMain.run(
          new String[] {
            output.resolve("missing-manifest").toString(),
            output.resolve("missing-evidence").toString(),
            output.resolve("report").toString()
          });
    }
  }

  static boolean sameProcess(long pid, String startedAt) {
    try {
      return ProcessHandle.of(pid)
          .filter(ProcessHandle::isAlive)
          .flatMap(p -> p.info().startInstant())
          .map(at -> at.toEpochMilli() == Instant.parse(startedAt).toEpochMilli())
          .orElse(false);
    } catch (Exception unavailable) {
      return false;
    }
  }

  private static SreCapture.WorkerSnapshot snapshot(Path file, Instant runEnd) throws Exception {
    SreCapture.WorkerSnapshot value = null;
    for (int attempt = 0; attempt < 12; attempt++) {
      value = JSON.readValue(Files.readAllBytes(file), SreCapture.WorkerSnapshot.class);
      if (!Instant.parse(value.observedAt()).isBefore(runEnd)) return value;
      Thread.sleep(250);
    }
    return value; // Normalizer marks the outdated cut incomplete.
  }

  private static DynamoDbClient localDynamo() {
    return DynamoDbClient.builder()
        .endpointOverride(URI.create("http://127.0.0.1:8000"))
        .region(Region.SA_EAST_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .httpClient(
            UrlConnectionHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(1))
                .socketTimeout(Duration.ofSeconds(2))
                .build())
        .overrideConfiguration(
            config ->
                config
                    .apiCallTimeout(Duration.ofSeconds(3))
                    .apiCallAttemptTimeout(Duration.ofSeconds(2)))
        .build();
  }

  static SreCapture.InboxFact readInbox(
      DynamoDbClient dynamo, JtlCapture.Sample sample, boolean fingerprint, Instant cut) {
    String canonical = sample.technicalKey();
    if (fingerprint) {
      var alias =
          dynamo
              .getItem(
                  GetItemRequest.builder()
                      .tableName("ProposalDeduplication")
                      .consistentRead(true)
                      .key(
                          Map.of(
                              "PK",
                              s("HEADER#" + sample.tenant() + "#" + sample.technicalKey()),
                              "SK",
                              s("METADATA")))
                      .projectionExpression("proposalId")
                      .build())
              .item();
      if (alias.isEmpty()) return null;
      canonical = alias.get("proposalId").s();
    } else if (sample.canonicalId() != null && !sample.canonicalId().equals(canonical)) {
      throw new IllegalArgumentException("Fingerprint capture mode required");
    }
    var item =
        dynamo
            .getItem(
                GetItemRequest.builder()
                    .tableName(InboxAttributes.TABLE_NAME)
                    .consistentRead(true)
                    .key(Map.of(InboxAttributes.IDEMPOTENCY_KEY, s(canonical)))
                    .projectionExpression(
                        "idempotencyKey,tenantId,createdAt,amount,#lane,#status,routedAt")
                    .expressionAttributeNames(Map.of("#lane", "lane", "#status", "status"))
                    .build())
            .item();
    if (item.isEmpty()) return null;
    String lane = item.containsKey("lane") ? item.get("lane").s() : "unrouted";
    if (item.get("status").s().equals("DISPATCHED") && lane.equals("unrouted")) {
      throw new IllegalArgumentException("Routed lane missing in old Inbox record");
    }
    if (!lane.equals("unrouted")) {
      if (!item.containsKey("routedAt"))
        throw new IllegalArgumentException("Routing timestamp missing");
      if (Instant.ofEpochMilli(Long.parseLong(item.get("routedAt").n())).isAfter(cut))
        lane = "unrouted";
    }
    return new SreCapture.InboxFact(
        item.get("idempotencyKey").s(),
        item.get("tenantId").s(),
        Instant.ofEpochMilli(Long.parseLong(item.get("createdAt").n())),
        lane,
        new BigDecimal(item.get("amount").n()).movePointRight(2).longValueExact());
  }

  private static AttributeValue s(String value) {
    return AttributeValue.builder().s(value).build();
  }

  private static void write(Path file, Object value) throws Exception {
    Files.writeString(
        file,
        JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value),
        StandardOpenOption.CREATE_NEW);
  }
}
