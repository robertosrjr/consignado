package br.com.consignado.platform.localenv.sre;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SreReportPrometheusTest {
  private static final Instant START = Instant.parse("2026-10-17T12:00:00Z");

  private static SreReport.Policy policy() {
    return new SreReport.Policy(
        "fixture",
        START,
        START.plusSeconds(60),
        START.plusSeconds(3600),
        1,
        120,
        2700,
        300,
        0.999,
        0.95,
        0.90,
        0.95);
  }

  @Test
  void exportsSnapshotGaugesAndPreservesFailureWithoutIdentifiers() {
    var evidence =
        new SreReport.Evidence(
            true,
            true,
            true,
            List.of(
                new SreReport.Attempt(
                    "a1", "private-proposal", START, 50, 202, true, "none", "RECEIVED")),
            List.of(new SreReport.Acceptance("private-proposal", START, "fast", 10000)),
            List.of());
    String metrics = SreReportPrometheus.render(policy(), SreReport.evaluate(policy(), evidence));
    assertThat(metrics)
        .contains(
            "sre_experiment_report_status 1\n",
            "sre_experiment_coverage_complete 1\n",
            "sre_experiment_indicator_ratio{sli=\"A\"} 1.0\n",
            "sre_experiment_indicator_ratio{sli=\"C_fast\"} 0.0\n",
            "sre_experiment_overdue_open 1\n",
            "sre_experiment_received_cents 10000\n",
            "sre_experiment_confirmed_cents 0\n",
            "sre_experiment_rejected_cents 0\n",
            "sre_experiment_open_cents 10000\n",
            "# TYPE sre_experiment_indicator_ratio gauge\n");
    assertThat(metrics)
        .doesNotContain(
            "private-proposal", "counter", "_total", "indicator_ratio{sli=\"C_standard\"}");
    assertThat(metrics).endsWith("\n");
  }

  @Test
  void incompleteCoverageSuppressesAllSlisInsteadOfDisplayingHealthyRatios() {
    var evidence = new SreReport.Evidence(false, false, false, List.of(), List.of(), List.of());
    String metrics = SreReportPrometheus.render(policy(), SreReport.evaluate(policy(), evidence));
    assertThat(metrics)
        .contains("sre_experiment_report_status 2\n", "sre_experiment_coverage_complete 0\n");
    assertThat(metrics).doesNotContain("indicator_ratio", "sre_experiment_accepted", "_cents");
    assertThat(SreReportPrometheus.incomplete()).doesNotContain("indicator_ratio");
  }

  @Test
  void exportsReconciledCentsWithoutCountingReplayOrTechnicalFailureAsBusinessSuccess() {
    var policy =
        new SreReport.Policy(
            "fixture",
            START,
            START.plusSeconds(60),
            START.plusSeconds(3600),
            4,
            120,
            2700,
            300,
            0.999,
            0.95,
            0.90,
            0.95);
    var evidence =
        new SreReport.Evidence(
            true,
            true,
            true,
            List.of(
                new SreReport.Attempt("a1", "p1", START, 50, 202, true, "none", "RECEIVED"),
                new SreReport.Attempt("a2", "p2", START, 50, 202, true, "none", "RECEIVED"),
                new SreReport.Attempt("a3", "p3", START, 50, 202, true, "none", "RECEIVED"),
                new SreReport.Attempt(
                    "a4", "p1", START.plusSeconds(1), 50, 202, true, "none", "DUPLICATE_ACCEPTED")),
            List.of(
                new SreReport.Acceptance("p1", START, "fast", 10001),
                new SreReport.Acceptance("p2", START, "fast", 20002),
                new SreReport.Acceptance("p3", START, "standard", 30003)),
            List.of(
                new SreReport.Decision("p1", START.plusSeconds(2), "confirmed"),
                new SreReport.Decision("p2", START.plusSeconds(2), "business_rejected"),
                new SreReport.Decision("p3", START.plusSeconds(2), "technical_failure")));
    String metrics = SreReportPrometheus.render(policy, SreReport.evaluate(policy, evidence));
    assertThat(metrics)
        .contains(
            "sre_experiment_coverage_complete 1\n",
            "sre_experiment_accepted 3\n",
            "sre_experiment_confirmed 1\n",
            "sre_experiment_rejected 1\n",
            "sre_experiment_open 1\n",
            "# TYPE sre_experiment_received_cents gauge\n",
            "sre_experiment_received_cents 60006\n",
            "sre_experiment_confirmed_cents 10001\n",
            "sre_experiment_rejected_cents 20002\n",
            "sre_experiment_open_cents 30003\n");
  }
}
