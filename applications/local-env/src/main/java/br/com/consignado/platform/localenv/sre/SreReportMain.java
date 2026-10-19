package br.com.consignado.platform.localenv.sre;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Local CLI: reads normalized evidence only; never connects to AWS or starts services. */
public final class SreReportMain {
  private static final JsonMapper JSON =
      JsonMapper.builder()
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .build();

  private SreReportMain() {}

  public record Deadlines(long fast, long standard, long unrouted) {}

  public record Targets(double acceptance, double fast, double standard, double unrouted) {}

  public record Manifest(
      int schemaVersion,
      String policyVersion,
      String start,
      String end,
      String asOf,
      long expectedAttempts,
      Deadlines deadlineSeconds,
      Targets targets) {}

  public record Coverage(boolean client, boolean inbox, boolean decisions) {}

  public record HttpRow(
      String id,
      String canonicalId,
      String startedAt,
      long elapsedMillis,
      int status,
      boolean eligible,
      String exclusion,
      String responseKind) {}

  public record InboxRow(String canonicalId, String receivedAt, String lane, long amountCents) {}

  public record DecisionRow(String canonicalId, String decidedAt, String outcome) {}

  public record Input(
      int schemaVersion,
      Coverage coverage,
      List<HttpRow> attempts,
      List<InboxRow> acceptances,
      List<DecisionRow> decisions) {}

  public static void main(String[] args) {
    int code = run(args);
    System.out.println(
        "SRE report exit=" + code + " (0=pass, 2=incomplete, 3=fail, 4=observing/no-events)");
    System.exit(code);
  }

  public static int run(String[] args) {
    if (args.length != 3) return 2;
    Path output;
    try {
      output = Path.of(args[2]);
      // Preserve evidence and previous reports. The destination must be a fresh directory.
      Files.createDirectory(output);
    } catch (Exception error) {
      return 2;
    }
    try {
      byte[] manifestBytes = Files.readAllBytes(Path.of(args[0]));
      byte[] evidenceBytes = Files.readAllBytes(Path.of(args[1]));
      Manifest manifest = JSON.readValue(manifestBytes, Manifest.class);
      Input input = JSON.readValue(evidenceBytes, Input.class);
      if (manifest.schemaVersion() != 1 || input.schemaVersion() != 1) {
        throw new IllegalArgumentException("Unsupported schema");
      }
      SreReport.Policy policy =
          new SreReport.Policy(
              manifest.policyVersion(),
              Instant.parse(manifest.start()),
              Instant.parse(manifest.end()),
              Instant.parse(manifest.asOf()),
              manifest.expectedAttempts(),
              manifest.deadlineSeconds().fast(),
              manifest.deadlineSeconds().standard(),
              manifest.deadlineSeconds().unrouted(),
              manifest.targets().acceptance(),
              manifest.targets().fast(),
              manifest.targets().standard(),
              manifest.targets().unrouted());
      SreReport.Evidence evidence =
          new SreReport.Evidence(
              input.coverage().client(),
              input.coverage().inbox(),
              input.coverage().decisions(),
              input.attempts().stream()
                  .map(
                      row ->
                          new SreReport.Attempt(
                              row.id(),
                              row.canonicalId(),
                              Instant.parse(row.startedAt()),
                              row.elapsedMillis(),
                              row.status(),
                              row.eligible(),
                              row.exclusion(),
                              row.responseKind()))
                  .toList(),
              input.acceptances().stream()
                  .map(
                      row ->
                          new SreReport.Acceptance(
                              row.canonicalId(),
                              Instant.parse(row.receivedAt()),
                              row.lane(),
                              row.amountCents()))
                  .toList(),
              input.decisions().stream()
                  .map(
                      row ->
                          new SreReport.Decision(
                              row.canonicalId(), Instant.parse(row.decidedAt()), row.outcome()))
                  .toList());
      SreReport.Result result = SreReport.evaluate(policy, evidence);
      Map<String, Object> document = new LinkedHashMap<>();
      document.put("schemaVersion", 1);
      document.put("status", result.status());
      document.put("manifestSha256", sha256(manifestBytes));
      document.put("evidenceSha256", sha256(evidenceBytes));
      document.put("measurement", result);
      Map<String, Object> indicators = new LinkedHashMap<>();
      result
          .indicators()
          .forEach(
              (name, indicator) -> {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("eligible", indicator.eligible());
                fields.put("good", indicator.good());
                fields.put("bad", indicator.bad());
                fields.put("target", indicator.target());
                fields.put("ratio", indicator.ratio());
                fields.put("budgetConsumed", indicator.budgetConsumed());
                indicators.put(name, fields);
              });
      document.put("indicators", indicators);
      write(
          output.resolve("report.json"),
          JSON.writerWithDefaultPrettyPrinter().writeValueAsString(document));
      write(output.resolve("report.md"), markdown(result));
      write(output.resolve("report.prom"), SreReportPrometheus.render(policy, result));
      return switch (result.status()) {
        case "PASS" -> 0;
        case "FAIL" -> 3;
        case "OBSERVING", "NO_EVENTS" -> 4;
        default -> 2;
      };
    } catch (Exception error) {
      // Do not echo input contents, exception messages, identifiers, or file paths.
      try {
        Files.deleteIfExists(output.resolve("report.json"));
        Files.deleteIfExists(output.resolve("report.md"));
        Files.deleteIfExists(output.resolve("report.prom"));
        write(
            output.resolve("report.json"),
            "{\"schemaVersion\":1,\"status\":\"INCOMPLETE\",\"issues\":[\"input_or_output_unavailable_or_invalid\"]}\n");
        write(
            output.resolve("report.md"),
            "# Relatório SRE\n\nEstado: **INCOMPLETE**. Entrada ou saída inválida/indisponível; nenhuma aprovação produzida.\n");
        write(output.resolve("report.prom"), SreReportPrometheus.incomplete());
      } catch (Exception unavailable) {
        // Nonzero process exit remains the authoritative signal if disk output fails.
      }
      return 2;
    }
  }

  private static String sha256(byte[] value) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
  }

  private static void write(Path file, String text) throws Exception {
    Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
  }

  private static String markdown(SreReport.Result result) {
    StringBuilder text =
        new StringBuilder("# Relatório SRE\n\nEstado: **")
            .append(result.status())
            .append("**. Política: `")
            .append(result.policy())
            .append("`. Corte: ")
            .append(result.asOf())
            .append(".\n\n")
            .append(
                "| Indicador | Bons | Ruins | Elegíveis | SLI | Meta |\n|---|---:|---:|---:|---:|---:|\n");
    result.indicators().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              SreReport.Indicator indicator = entry.getValue();
              text.append("| ")
                  .append(entry.getKey())
                  .append(" | ")
                  .append(indicator.good())
                  .append(" | ")
                  .append(indicator.bad())
                  .append(" | ")
                  .append(indicator.eligible())
                  .append(" | ")
                  .append(
                      indicator.ratio() == null
                          ? "sem eventos"
                          : String.format(java.util.Locale.ROOT, "%.2f%%", 100 * indicator.ratio()))
                  .append(" | ")
                  .append(String.format(java.util.Locale.ROOT, "%.2f%%", 100 * indicator.target()))
                  .append(" |\n");
            });
    text.append("\nAceitas: ")
        .append(result.accepted())
        .append("; confirmadas: ")
        .append(result.confirmed())
        .append("; rejeitadas: ")
        .append(result.rejected())
        .append("; abertas: ")
        .append(result.open())
        .append("; abertas vencidas: ")
        .append(result.overdueOpen())
        .append("; prazos ainda não vencidos: ")
        .append(result.notYetDue())
        .append("; tentativas excluídas: ")
        .append(result.excludedAttempts())
        .append(".\n\nValores em centavos: recebido=")
        .append(result.receivedCents())
        .append("; confirmado=")
        .append(result.confirmedCents())
        .append("; rejeitado=")
        .append(result.rejectedCents())
        .append("; aberto=")
        .append(result.openCents())
        .append(".\n");
    if (!result.issues().isEmpty())
      text.append("\nCobertura/inconsistências: ")
          .append(String.join(", ", result.issues()))
          .append(".\n");
    text.append(
        "\nAvaliação offline das fontes fornecidas; não comprova durabilidade, ausência universal de efeito duplicado ou SLA mensal.\n");
    return text.toString();
  }
}
