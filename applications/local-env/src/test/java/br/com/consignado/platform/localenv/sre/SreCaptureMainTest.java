package br.com.consignado.platform.localenv.sre;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class SreCaptureMainTest {
  @TempDir Path directory;

  @Test
  void changedJtlFailsBeforeOpeningBackendAndProducesIncompleteArtifacts() throws Exception {
    Path jtl = directory.resolve("test.jtl");
    Files.writeString(jtl, "tampered");
    var control =
        new SreCaptureMain.Control(
            1,
            jtl.toString(),
            "not-the-file-hash",
            directory.resolve("summary.json").toString(),
            "not-the-summary-hash",
            Instant.now().minusSeconds(2).toString(),
            Instant.now().toString(),
            true,
            1,
            "worker-instance",
            1,
            Instant.now().toString(),
            false);
    Path controlFile = directory.resolve("control.json");
    var json = JsonMapper.builder().build();
    Files.writeString(controlFile, json.writeValueAsString(control));
    Path policyFile = directory.resolve("policy.json");
    Files.writeString(
        policyFile,
        json.writeValueAsString(
            new SreCaptureMain.Policy(
                1,
                "test",
                new SreReportMain.Deadlines(120, 2700, 300),
                new SreReportMain.Targets(0.999, 0.95, 0.90, 0.95))));
    Path output = directory.resolve("capture");
    assertThat(
            SreCaptureMain.run(
                new String[] {
                  controlFile.toString(), policyFile.toString(), "missing-worker", output.toString()
                }))
        .isEqualTo(2);
    assertThat(Files.readString(output.resolve("capture-error.json")))
        .contains("INCOMPLETE")
        .doesNotContain("tampered");
    assertThat(Files.readString(output.resolve("report/report.prom")))
        .contains("sre_experiment_coverage_complete 0");
    assertThat(
            SreCaptureMain.run(
                new String[] {
                  controlFile.toString(), policyFile.toString(), "missing-worker", output.toString()
                }))
        .isEqualTo(2);
  }

  @Test
  void processStartTimeDistinguishesCurrentSessionFromPidReuse() {
    var process = ProcessHandle.current();
    Instant start = process.info().startInstant().orElseThrow();
    assertThat(SreCaptureMain.sameProcess(process.pid(), start.toString())).isTrue();
    assertThat(SreCaptureMain.sameProcess(process.pid(), start.minusMillis(1).toString()))
        .isFalse();
    assertThat(SreCaptureMain.sameProcess(-1, start.toString())).isFalse();
  }
}
