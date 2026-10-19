package br.com.consignado.platform.localenv.sre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SreReportTest {
  private static final Instant START = Instant.parse("2026-10-17T12:00:00Z");

  private static SreReport.Policy policy(long attempts, long asOfSeconds) {
    return new SreReport.Policy(
        "poc-v1",
        START,
        START.plusSeconds(60),
        START.plusSeconds(asOfSeconds),
        attempts,
        120,
        2700,
        300,
        0.999,
        0.95,
        0.90,
        0.95);
  }

  private static SreReport.Attempt attempt(String id, String canonical, int status, String kind) {
    return new SreReport.Attempt(id, canonical, START, 50, status, true, "none", kind);
  }

  private static SreReport.Acceptance acceptance(String canonical, String lane) {
    return new SreReport.Acceptance(canonical, START.plusMillis(10), lane, 100_00);
  }

  private static SreReport.Decision decision(String canonical, long seconds, String outcome) {
    return new SreReport.Decision(canonical, START.plusSeconds(seconds), outcome);
  }

  private static SreReport.Result evaluate(
      SreReport.Policy policy,
      List<SreReport.Attempt> attempts,
      List<SreReport.Acceptance> acceptances,
      List<SreReport.Decision> decisions) {
    return SreReport.evaluate(
        policy, new SreReport.Evidence(true, true, true, attempts, acceptances, decisions));
  }

  @Test
  void fastFinishersDoNotHideLateAndStuckProposals() {
    var result =
        evaluate(
            policy(3, 3600),
            List.of(
                attempt("a1", "p1", 202, "RECEIVED"),
                attempt("a2", "p2", 202, "RECEIVED"),
                attempt("a3", "p3", 202, "RECEIVED")),
            List.of(acceptance("p1", "fast"), acceptance("p2", "fast"), acceptance("p3", "fast")),
            List.of(decision("p1", 30, "confirmed"), decision("p2", 180, "confirmed")));
    assertThat(result.status()).isEqualTo("FAIL");
    assertThat(result.indicators().get("A").ratio()).isEqualTo(1.0);
    assertThat(result.indicators().get("C_fast")).isEqualTo(new SreReport.Indicator(1, 2, 0.95));
    assertThat(result.open()).isEqualTo(1);
    assertThat(result.overdueOpen()).isEqualTo(1);
    assertThat(result.receivedCents())
        .isEqualTo(result.confirmedCents() + result.rejectedCents() + result.openCents());
  }

  @Test
  void replayAndRepeatedEvidenceDoNotCreateAnotherProposalOrDeadline() {
    var received = attempt("a1", "p1", 202, "RECEIVED");
    var replay =
        new SreReport.Attempt(
            "a2", "p1", START.plusSeconds(1), 50, 202, true, "none", "DUPLICATE_ACCEPTED");
    var accepted = acceptance("p1", "fast");
    var settled = decision("p1", 20, "confirmed");
    var result =
        evaluate(
            policy(2, 3600),
            List.of(received, received, replay),
            List.of(accepted, accepted),
            List.of(settled, settled));
    assertThat(result.status()).isEqualTo("PASS");
    assertThat(result.indicators().get("A").eligible()).isEqualTo(2);
    assertThat(result.accepted()).isEqualTo(1);
    assertThat(result.receivedCents()).isEqualTo(100_00);
    assertThat(result.indicators().get("C_fast").eligible()).isEqualTo(1);
  }

  @Test
  void timeoutWithPersistedAcceptanceRemainsBadHttpAndCanHaveGoodBusinessOutcome() {
    var result =
        evaluate(
            policy(1, 3600),
            List.of(attempt("a1", "p1", 0, "NONE")),
            List.of(acceptance("p1", "fast")),
            List.of(decision("p1", 20, "confirmed")));
    assertThat(result.indicators().get("A").good()).isZero();
    assertThat(result.indicators().get("B_100ms").good()).isZero();
    assertThat(result.indicators().get("C_fast").good()).isEqualTo(1);
  }

  @Test
  void businessRejectionIsGoodButTechnicalFailureAndUnroutedAreStillOpen() {
    var result =
        evaluate(
            policy(3, 3600),
            List.of(
                attempt("a1", "p1", 202, "RECEIVED"),
                attempt("a2", "p2", 202, "RECEIVED"),
                attempt("a3", "p3", 202, "RECEIVED")),
            List.of(
                acceptance("p1", "standard"),
                acceptance("p2", "fast"),
                acceptance("p3", "unrouted")),
            List.of(
                decision("p1", 15, "business_rejected"), decision("p2", 15, "technical_failure")));
    assertThat(result.indicators().get("C_standard").good()).isEqualTo(1);
    assertThat(result.indicators().get("C_fast").bad()).isEqualTo(1);
    assertThat(result.indicators().get("C_unrouted").bad()).isEqualTo(1);
    assertThat(result.rejected()).isEqualTo(1);
    assertThat(result.open()).isEqualTo(2);
  }

  @Test
  void immatureProposalsAreObservedWithoutApprovalAndExactDeadlineIsInclusive() {
    var accepted = new SreReport.Acceptance("p1", START, "fast", 100);
    var waiting =
        evaluate(
            policy(1, 60),
            List.of(attempt("a1", "p1", 202, "RECEIVED")),
            List.of(accepted),
            List.of());
    assertThat(waiting.status()).isEqualTo("OBSERVING");
    assertThat(waiting.indicators().get("C_fast").ratio()).isNull();
    var onTime =
        evaluate(
            policy(1, 120),
            List.of(attempt("a1", "p1", 202, "RECEIVED")),
            List.of(accepted),
            List.of(decision("p1", 120, "confirmed")));
    assertThat(onTime.status()).isEqualTo("PASS");
    var expired =
        evaluate(
            policy(1, 120),
            List.of(attempt("a1", "p1", 202, "RECEIVED")),
            List.of(accepted),
            List.of());
    assertThat(expired.indicators().get("C_fast").bad()).isEqualTo(1);
  }

  @Test
  void emptyPopulationAndCoverageGapsCannotProducePass() {
    var empty = evaluate(policy(0, 3600), List.of(), List.of(), List.of());
    assertThat(empty.status()).isEqualTo("NO_EVENTS");
    assertThat(empty.indicators().get("A").ratio()).isNull();
    assertThat(empty.indicators().get("A").budgetConsumed()).isNull();
    var gap =
        SreReport.evaluate(
            policy(0, 3600),
            new SreReport.Evidence(true, true, false, List.of(), List.of(), List.of()));
    assertThat(gap.status()).isEqualTo("INCOMPLETE");
    assertThat(gap.issues()).contains("decisions_coverage_incomplete");
    assertThat(evaluate(policy(1, 3600), List.of(), List.of(), List.of()).status())
        .isEqualTo("INCOMPLETE");
  }

  @Test
  void rejectsConflictingSnapshotsAndInvalidEligibilityOrTimestamps() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                evaluate(
                    policy(1, 3600),
                    List.of(attempt("a1", "p1", 202, "RECEIVED")),
                    List.of(acceptance("p1", "fast"), acceptance("p1", "standard")),
                    List.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SreReport.Attempt("a1", "p1", START, 1, 429, false, "none", "NONE"));
    var future =
        evaluate(
            policy(1, 3600),
            List.of(attempt("a1", "p1", 202, "RECEIVED")),
            List.of(acceptance("p1", "fast")),
            List.of(decision("p1", 7200, "confirmed")));
    assertThat(future.status()).isEqualTo("INCOMPLETE");
    assertThat(future.issues()).contains("future_decision");
    var reversed =
        evaluate(
            policy(1, 3600),
            List.of(attempt("a1", "p1", 202, "RECEIVED")),
            List.of(acceptance("p1", "fast")),
            List.of(decision("p1", -1, "confirmed")));
    assertThat(reversed.issues()).contains("decision_before_acceptance");
  }

  @Test
  void expectedClientRejectionsAreExcludedButInQuota429IsBad() {
    var invalid =
        new SreReport.Attempt("a1", null, START, 10, 422, false, "invalid_request", "NONE");
    var throttled = attempt("a2", "p2", 429, "NONE");
    var result = evaluate(policy(2, 3600), List.of(invalid, throttled), List.of(), List.of());
    assertThat(result.excludedAttempts()).isEqualTo(1);
    assertThat(result.indicators().get("A").eligible()).isEqualTo(1);
    assertThat(result.indicators().get("A").bad()).isEqualTo(1);
  }

  @Test
  void previousCohortReplayDoesNotInflateThisCohortAndMissingClientEvidenceIsIncomplete() {
    var previous = new SreReport.Acceptance("p1", START.minusSeconds(30), "fast", 100);
    var result =
        evaluate(
            policy(1, 3600),
            List.of(attempt("a1", "p1", 202, "DUPLICATE_ACCEPTED")),
            List.of(previous),
            List.of(decision("p1", 5, "confirmed")));
    assertThat(result.indicators().get("A").good()).isEqualTo(1);
    assertThat(result.accepted()).isZero();
    var missing =
        evaluate(policy(0, 3600), List.of(), List.of(acceptance("p1", "fast")), List.of());
    assertThat(missing.status()).isEqualTo("INCOMPLETE");
  }

  @Test
  void a202WithoutPersistedEvidenceIsBadAndLatencyUsesInclusiveLimits() {
    var missing =
        evaluate(
            policy(1, 3600), List.of(attempt("a1", "p1", 202, "RECEIVED")), List.of(), List.of());
    assertThat(missing.status()).isEqualTo("FAIL");
    assertThat(missing.indicators().get("A").bad()).isEqualTo(1);
    var first = new SreReport.Attempt("a1", "p1", START, 100, 202, true, "none", "RECEIVED");
    var second = new SreReport.Attempt("a2", "p2", START, 200, 202, true, "none", "RECEIVED");
    var result =
        evaluate(
            policy(2, 3600),
            List.of(first, second),
            List.of(acceptance("p1", "fast"), acceptance("p2", "fast")),
            List.of(decision("p1", 1, "confirmed"), decision("p2", 1, "confirmed")));
    assertThat(result.indicators().get("B_100ms").good()).isEqualTo(1);
    assertThat(result.indicators().get("B_200ms").good()).isEqualTo(2);
  }
}
