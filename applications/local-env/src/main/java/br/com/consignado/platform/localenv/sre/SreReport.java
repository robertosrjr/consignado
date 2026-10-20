package br.com.consignado.platform.localenv.sre;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Offline measurement policy. Does not infer business facts from traces or metric deltas. */
public final class SreReport {
  private SreReport() {}

  public record Policy(
      String version,
      Instant start,
      Instant end,
      Instant asOf,
      long expectedAttempts,
      long fastSeconds,
      long standardSeconds,
      long unroutedSeconds,
      double acceptanceTarget,
      double fastTarget,
      double standardTarget,
      double unroutedTarget) {
    public Policy {
      require(version != null && version.matches("[a-zA-Z0-9._-]{1,64}"));
      require(start != null && end != null && asOf != null);
      require(start.isBefore(end) && !asOf.isBefore(end));
      require(expectedAttempts >= 0);
      require(fastSeconds > 0 && standardSeconds > 0 && unroutedSeconds > 0);
      for (double target :
          new double[] {acceptanceTarget, fastTarget, standardTarget, unroutedTarget}) {
        require(Double.isFinite(target) && target > 0 && target < 1);
      }
    }

    long seconds(String lane) {
      return switch (lane) {
        case "fast" -> fastSeconds;
        case "standard" -> standardSeconds;
        case "unrouted" -> unroutedSeconds;
        default -> throw new IllegalArgumentException("Invalid lane");
      };
    }
  }

  public record Attempt(
      String id,
      String canonicalId,
      Instant startedAt,
      long elapsedMillis,
      int status,
      boolean eligible,
      String exclusion,
      String responseKind) {
    public Attempt {
      identifier(id);
      if (canonicalId != null) identifier(canonicalId);
      require(
          startedAt != null
              && elapsedMillis >= 0
              && (status == 0 || status >= 100 && status <= 599));
      require(Set.of("none", "invalid_request", "invalid_auth", "over_quota").contains(exclusion));
      require(eligible == exclusion.equals("none"));
      require(Set.of("RECEIVED", "DUPLICATE_ACCEPTED", "NONE").contains(responseKind));
    }
  }

  public record Acceptance(String canonicalId, Instant receivedAt, String lane, long amountCents) {
    public Acceptance {
      identifier(canonicalId);
      require(receivedAt != null && amountCents >= 0);
      require(Set.of("fast", "standard", "unrouted").contains(lane));
    }
  }

  public record Decision(String canonicalId, Instant decidedAt, String outcome) {
    public Decision {
      identifier(canonicalId);
      require(decidedAt != null);
      require(Set.of("confirmed", "business_rejected", "technical_failure").contains(outcome));
    }

    boolean correct() {
      return !outcome.equals("technical_failure");
    }
  }

  public record Evidence(
      boolean clientComplete,
      boolean inboxComplete,
      boolean decisionsComplete,
      List<Attempt> attempts,
      List<Acceptance> acceptances,
      List<Decision> decisions) {}

  public record Indicator(long good, long bad, double target) {
    public long eligible() {
      return good + bad;
    }

    public Double ratio() {
      return eligible() == 0 ? null : (double) good / eligible();
    }

    public Double budgetConsumed() {
      return eligible() == 0 ? null : bad / (eligible() * (1 - target));
    }

    public boolean breached() {
      return ratio() != null && ratio() < target;
    }
  }

  public record Result(
      String status,
      String policy,
      String asOf,
      List<String> issues,
      Map<String, Indicator> indicators,
      long excludedAttempts,
      long accepted,
      long confirmed,
      long rejected,
      long open,
      long overdueOpen,
      long notYetDue,
      long receivedCents,
      long confirmedCents,
      long rejectedCents,
      long openCents) {}

  public static Result evaluate(Policy policy, Evidence evidence) {
    List<String> issues = new ArrayList<>();
    if (!evidence.clientComplete()) issues.add("client_coverage_incomplete");
    if (!evidence.inboxComplete()) issues.add("inbox_coverage_incomplete");
    if (!evidence.decisionsComplete()) issues.add("decisions_coverage_incomplete");
    Map<String, Attempt> attempts = unique(evidence.attempts(), Attempt::id);
    Map<String, Acceptance> acceptances = unique(evidence.acceptances(), Acceptance::canonicalId);
    Map<String, Decision> decisions = unique(evidence.decisions(), Decision::canonicalId);
    Set<String> observedCanonicalIds = new HashSet<>();
    for (Attempt attempt : attempts.values()) observedCanonicalIds.add(attempt.canonicalId());
    if (attempts.size() != policy.expectedAttempts()) issues.add("client_attempt_count_mismatch");
    for (Decision decision : decisions.values()) {
      Acceptance acceptance = acceptances.get(decision.canonicalId());
      if (acceptance == null) issues.add("decision_without_acceptance");
      else if (decision.decidedAt().isBefore(acceptance.receivedAt())) {
        issues.add("decision_before_acceptance");
      }
      if (decision.decidedAt().isAfter(policy.asOf())) issues.add("future_decision");
    }
    for (Acceptance acceptance : acceptances.values()) {
      if (acceptance.receivedAt().isAfter(policy.asOf())) issues.add("future_acceptance");
      if (within(acceptance.receivedAt(), policy)
          && !observedCanonicalIds.contains(acceptance.canonicalId())) {
        issues.add("acceptance_without_client_evidence");
      }
    }
    long eligible = 0, good = 0, fast = 0, slower = 0, excluded = 0;
    for (Attempt attempt : attempts.values()) {
      if (!within(attempt.startedAt(), policy)) {
        issues.add("attempt_outside_cohort");
        continue;
      }
      Instant finished = attempt.startedAt().plusMillis(attempt.elapsedMillis());
      if (finished.isAfter(policy.asOf())) issues.add("future_attempt");
      if (!attempt.eligible()) {
        if (attempt.status() == 202) issues.add("accepted_ineligible_attempt");
        excluded++;
        continue;
      }
      eligible++;
      Acceptance acceptance = acceptances.get(attempt.canonicalId());
      boolean valid =
          attempt.status() == 202
              && !attempt.responseKind().equals("NONE")
              && acceptance != null
              && !acceptance.receivedAt().isAfter(finished)
              && (!attempt.responseKind().equals("RECEIVED")
                  || !acceptance.receivedAt().isBefore(attempt.startedAt()));
      if (valid) {
        good++;
        if (attempt.elapsedMillis() <= 100) fast++;
        if (attempt.elapsedMillis() <= 200) slower++;
      }
    }
    Map<String, Indicator> indicators = new LinkedHashMap<>();
    indicators.put("A", new Indicator(good, eligible - good, policy.acceptanceTarget()));
    indicators.put("B_100ms", new Indicator(fast, eligible - fast, 0.95));
    indicators.put("B_200ms", new Indicator(slower, eligible - slower, 0.99));
    Map<String, long[]> lanes = new LinkedHashMap<>();
    for (String lane : List.of("fast", "standard", "unrouted")) lanes.put(lane, new long[2]);
    long accepted = 0, confirmed = 0, rejected = 0, open = 0, overdue = 0, notDue = 0;
    long receivedCents = 0, confirmedCents = 0, rejectedCents = 0, openCents = 0;
    for (Acceptance acceptance : acceptances.values()) {
      if (!within(acceptance.receivedAt(), policy)) continue;
      accepted++;
      receivedCents = Math.addExact(receivedCents, acceptance.amountCents());
      Decision decision = decisions.get(acceptance.canonicalId());
      boolean complete = decision != null && decision.correct();
      if (complete && decision.outcome().equals("confirmed")) {
        confirmed++;
        confirmedCents = Math.addExact(confirmedCents, acceptance.amountCents());
      } else if (complete) {
        rejected++;
        rejectedCents = Math.addExact(rejectedCents, acceptance.amountCents());
      } else {
        open++;
        openCents = Math.addExact(openCents, acceptance.amountCents());
      }
      Instant deadline = acceptance.receivedAt().plusSeconds(policy.seconds(acceptance.lane()));
      // The observation cut is closed: at the deadline, a still-open item is already bad.
      if (deadline.isAfter(policy.asOf())) {
        notDue++;
      } else {
        boolean onTime = complete && !decision.decidedAt().isAfter(deadline);
        lanes.get(acceptance.lane())[onTime ? 0 : 1]++;
        if (!complete) overdue++;
      }
    }
    indicators.put("C_fast", indicator(lanes.get("fast"), policy.fastTarget()));
    indicators.put("C_standard", indicator(lanes.get("standard"), policy.standardTarget()));
    indicators.put("C_unrouted", indicator(lanes.get("unrouted"), policy.unroutedTarget()));
    boolean breached = indicators.values().stream().anyMatch(Indicator::breached);
    String status =
        !issues.isEmpty()
            ? "INCOMPLETE"
            : breached
                ? "FAIL"
                : notDue > 0 ? "OBSERVING" : eligible == 0 && accepted == 0 ? "NO_EVENTS" : "PASS";
    return new Result(
        status,
        policy.version(),
        policy.asOf().toString(),
        List.copyOf(issues.stream().distinct().toList()),
        Collections.unmodifiableMap(new LinkedHashMap<>(indicators)),
        excluded,
        accepted,
        confirmed,
        rejected,
        open,
        overdue,
        notDue,
        receivedCents,
        confirmedCents,
        rejectedCents,
        openCents);
  }

  private static Indicator indicator(long[] counts, double target) {
    return new Indicator(counts[0], counts[1], target);
  }

  private static boolean within(Instant time, Policy policy) {
    return !time.isBefore(policy.start()) && time.isBefore(policy.end());
  }

  private static <T> Map<String, T> unique(
      List<T> values, java.util.function.Function<T, String> key) {
    Map<String, T> result = new LinkedHashMap<>();
    for (T value : values) {
      T previous = result.putIfAbsent(key.apply(value), value);
      require(previous == null || previous.equals(value));
    }
    return result;
  }

  private static void identifier(String value) {
    require(value != null && value.matches("[a-zA-Z0-9._-]{1,128}"));
  }

  private static void require(boolean condition) {
    if (!condition) throw new IllegalArgumentException("Invalid measurement evidence or policy");
  }
}
