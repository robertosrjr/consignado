package br.com.consignado.platform.localenv.sre;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SreReportMainTest {
  @TempDir Path temp;

  private Path fixture(String name) throws Exception {
    Path path = temp.resolve(name);
    try (var resource = getClass().getResourceAsStream("/sre/" + name)) {
      Files.copy(resource, path);
    }
    return path;
  }

  @Test
  void cliWritesAnAuditableFailureWithoutLeakingIndividualIdentifiersAndNeverOverwrites()
      throws Exception {
    Path manifest = fixture("manifest.json");
    Path evidence = fixture("evidence.json");
    Path output = temp.resolve("result");
    String[] args = {manifest.toString(), evidence.toString(), output.toString()};
    assertThat(SreReportMain.run(args)).isEqualTo(3);
    String json = Files.readString(output.resolve("report.json"));
    assertThat(json).contains("FAIL", "manifestSha256", "evidenceSha256", "budgetConsumed");
    assertThat(json).doesNotContain("canonicalId", "\"p1\"", "\"p2\"", "\"p3\"");
    assertThat(Files.readString(output.resolve("report.md")))
        .contains("33.33%", "abertas vencidas: 1");
    assertThat(Files.readString(output.resolve("report.prom")))
        .contains("sre_experiment_report_status 1\n", "sre_experiment_overdue_open 1\n");
    assertThat(SreReportMain.run(args)).isEqualTo(2);
    assertThat(Files.readString(output.resolve("report.json"))).isEqualTo(json);
  }

  @Test
  void unavailableSourceProducesIncompleteWithoutExceptionDetails() throws Exception {
    Path manifest = fixture("manifest.json");
    Path output = temp.resolve("missing");
    assertThat(
            SreReportMain.run(
                new String[] {
                  manifest.toString(),
                  temp.resolve("missing-source.json").toString(),
                  output.toString()
                }))
        .isEqualTo(2);
    assertThat(Files.readString(output.resolve("report.json")))
        .contains("INCOMPLETE")
        .doesNotContain(temp.toString(), "Exception");
  }

  @Test
  void unknownFieldsAndDuplicateJsonKeysAreRejected() throws Exception {
    Path manifest = fixture("manifest.json");
    Path evidence = fixture("evidence.json");
    String original = Files.readString(evidence);
    for (String extra : new String[] {"\"payload\":{},", "\"schemaVersion\":99,"}) {
      Files.writeString(evidence, original.replaceFirst("\\{", "{" + extra));
      Path output = temp.resolve("invalid-" + Math.abs(extra.hashCode()));
      assertThat(
              SreReportMain.run(
                  new String[] {manifest.toString(), evidence.toString(), output.toString()}))
          .isEqualTo(2);
      assertThat(Files.readString(output.resolve("report.json"))).contains("INCOMPLETE");
    }
  }

  @Test
  void missingFieldsAndConflictingSnapshotsCannotDefaultToSuccessfulMeasurements()
      throws Exception {
    Path manifest = fixture("manifest.json");
    Path evidence = fixture("evidence.json");
    String original = Files.readString(evidence);
    Files.writeString(evidence, original.replace("\"elapsedMillis\": 50,", ""));
    Path output = temp.resolve("missing-duration");
    assertThat(
            SreReportMain.run(
                new String[] {manifest.toString(), evidence.toString(), output.toString()}))
        .isEqualTo(2);
    assertThat(Files.readString(output.resolve("report.json"))).contains("INCOMPLETE");
  }
}
