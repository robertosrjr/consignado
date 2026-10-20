package br.com.consignado.platform.localenv.sre;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SreCaptureTest {
  private static final Instant START = Instant.parse("2026-10-17T12:00:00Z");

  private static JtlCapture.Sample sample(String id, String technical, String canonical) {
    return new JtlCapture.Sample(
        id,
        technical,
        "bank",
        canonical,
        START.plusSeconds(1),
        100,
        202,
        true,
        "none",
        "RECEIVED",
        10000);
  }

  private static SreCapture.WorkerSnapshot snapshot(
      String instance, Instant cut, List<SreCapture.WorkerState> states) {
    return new SreCapture.WorkerSnapshot(
        1, instance, START.minusSeconds(30).toString(), cut.toString(), states);
  }

  private static SreCapture.Normalized normalize(
      List<JtlCapture.Sample> samples,
      SreCapture.WorkerSnapshot snapshot,
      SreCapture.InboxSource source) {
    return SreCapture.normalize(
        samples,
        snapshot,
        "original",
        START,
        START.plusSeconds(5),
        START.plusSeconds(10),
        true,
        true,
        source);
  }

  @Test
  void independentInboxAndWorkerSourcesPreservePendingAndDoNotInventARejection() {
    var states =
        List.of(
            new SreCapture.WorkerState("p1", "fast", "CONFIRMED", START.plusSeconds(2).toString()),
            new SreCapture.WorkerState(
                "p2", "standard", "FAILED", START.plusSeconds(3).toString()));
    var normalized =
        normalize(
            List.of(sample("a1", "t1", "p1"), sample("a2", "t2", "p2"), sample("a3", "t3", "p3")),
            snapshot("original", START.plusSeconds(9), states),
            sample ->
                new SreCapture.InboxFact(
                    sample.canonicalId(),
                    "bank",
                    START.plusMillis(1050),
                    sample.canonicalId().equals("p2") ? "standard" : "fast",
                    10000));
    assertThat(normalized.input().coverage())
        .isEqualTo(new SreReportMain.Coverage(true, true, true));
    assertThat(normalized.input().acceptances()).hasSize(3);
    assertThat(normalized.input().decisions())
        .extracting(SreReportMain.DecisionRow::outcome)
        .containsExactly("confirmed", "technical_failure");
    assertThat(normalized.input().decisions())
        .extracting(SreReportMain.DecisionRow::canonicalId)
        .doesNotContain("p3");
  }

  @Test
  void aliasesNormalizeToOneAcceptanceAndReadFailureIsNotAnEmptyHealthyInbox() {
    var normalized =
        normalize(
            List.of(sample("a1", "alias1", "p1"), sample("a2", "alias2", "p1")),
            snapshot("original", START.plusSeconds(9), List.of()),
            sample ->
                new SreCapture.InboxFact("p1", "bank", START.plusMillis(1050), "unrouted", 10000));
    assertThat(normalized.input().acceptances()).hasSize(1);
    var unavailable =
        normalize(
            List.of(sample("a1", "t1", "p1")),
            snapshot("original", START.plusSeconds(9), List.of()),
            sample -> {
              throw new IllegalStateException("sensitive upstream details");
            });
    assertThat(unavailable.input().coverage().inbox()).isFalse();
    assertThat(unavailable.issues()).containsExactly("inbox_read_unavailable_or_inconsistent");
  }

  @Test
  void restartStaleSnapshotAndMissingTerminalTimestampInvalidateCoverage() {
    var restarted =
        normalize(
            List.of(), snapshot("new-instance", START.plusSeconds(9), List.of()), sample -> null);
    assertThat(restarted.input().coverage().decisions()).isFalse();
    var stale =
        normalize(List.of(), snapshot("original", START.plusSeconds(4), List.of()), sample -> null);
    assertThat(stale.input().coverage().decisions()).isFalse();
    var invalid =
        normalize(
            List.of(sample("a1", "t1", "p1")),
            snapshot(
                "original",
                START.plusSeconds(9),
                List.of(new SreCapture.WorkerState("p1", "fast", "CONFIRMED", null))),
            sample ->
                new SreCapture.InboxFact("p1", "bank", START.plusMillis(1050), "fast", 10000));
    assertThat(invalid.input().coverage().decisions()).isFalse();
  }

  @Test
  void tenantOrCanonicalContradictionsCannotApproveAnotherOperation() {
    var result =
        normalize(
            List.of(sample("a1", "t1", "p1")),
            snapshot("original", START.plusSeconds(9), List.of()),
            sample ->
                new SreCapture.InboxFact(
                    "other", "other-bank", START.plusMillis(1050), "fast", 10000));
    assertThat(result.input().coverage().inbox()).isFalse();
  }

  @Test
  void acceptedAmountMismatchAndLostSessionCannotBecomeHealthyEvidence() {
    var result =
        normalize(
            List.of(sample("a1", "t1", "p1")),
            snapshot("original", START.plusSeconds(9), List.of()),
            sample ->
                new SreCapture.InboxFact("p1", "bank", START.plusMillis(1050), "fast", 99999));
    assertThat(result.input().coverage().inbox()).isFalse();
    assertThat(result.issues()).contains("accepted_amount_mismatch");
    var reads = new java.util.concurrent.atomic.AtomicInteger();
    var lost =
        SreCapture.normalize(
            List.of(sample("a1", "t1", "p1")),
            snapshot("original", START.plusSeconds(9), List.of()),
            "original",
            START,
            START.plusSeconds(5),
            START.plusSeconds(10),
            true,
            false,
            sample -> {
              reads.incrementAndGet();
              throw new IllegalStateException();
            });
    assertThat(lost.input().coverage().inbox()).isFalse();
    assertThat(reads.get()).isZero();
  }
}
