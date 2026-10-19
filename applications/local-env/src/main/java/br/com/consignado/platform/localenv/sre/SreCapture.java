package br.com.consignado.platform.localenv.sre;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalizes independent local sources; source failures are never treated as an empty healthy set.
 */
public final class SreCapture {
  private SreCapture() {}

  public record WorkerState(String canonicalId, String lane, String status, String committedAt) {}

  public record WorkerSnapshot(
      int schemaVersion,
      String instanceId,
      String startedAt,
      String observedAt,
      List<WorkerState> states) {}

  public record InboxFact(
      String canonicalId, String tenant, Instant receivedAt, String lane, long amountCents) {}

  public interface InboxSource {
    InboxFact read(JtlCapture.Sample sample) throws Exception;
  }

  public record Normalized(SreReportMain.Input input, List<String> issues) {}

  public static Normalized normalize(
      List<JtlCapture.Sample> samples,
      WorkerSnapshot worker,
      String expectedWorkerInstance,
      Instant runStartedAt,
      Instant runEndedAt,
      Instant now,
      boolean clientComplete,
      boolean inboxSessionComplete,
      InboxSource inbox) {
    List<String> issues = new ArrayList<>();
    Instant cut = Instant.parse(worker.observedAt());
    boolean workerComplete =
        worker.schemaVersion() == 1
            && worker.instanceId().equals(expectedWorkerInstance)
            && !Instant.parse(worker.startedAt()).isAfter(runStartedAt)
            && !cut.isBefore(runEndedAt)
            && !cut.isAfter(now)
            && Duration.between(cut, now).compareTo(Duration.ofSeconds(5)) <= 0;
    if (!workerComplete) issues.add("worker_session_or_cut_invalid");
    if (!clientComplete) issues.add("client_run_incomplete");
    if (!inboxSessionComplete) issues.add("inbox_process_session_changed");
    Map<String, WorkerState> states = new LinkedHashMap<>();
    for (var state : worker.states()) {
      if (!List.of("PENDING", "SENT_TO_LEGACY", "CONFIRMED", "FAILED").contains(state.status())
          || !List.of("fast", "standard").contains(state.lane())
          || states.putIfAbsent(state.canonicalId(), state) != null) {
        throw new IllegalArgumentException("Invalid worker snapshot");
      }
    }
    boolean inboxComplete = inboxSessionComplete;
    boolean sourceAvailable = inboxSessionComplete;
    Map<String, InboxFact> unique = new LinkedHashMap<>();
    List<SreReportMain.HttpRow> attempts = new ArrayList<>();
    Map<String, InboxFact> reads = new LinkedHashMap<>();
    for (var sample : samples) {
      if (sample.startedAt().isBefore(runStartedAt)
          || sample.startedAt().plusMillis(sample.elapsedMillis()).isAfter(runEndedAt)) {
        clientComplete = false;
        issues.add("sample_outside_run");
      }
      InboxFact fact = null;
      try {
        if (!sourceAvailable) throw new IllegalStateException("Source coverage unavailable");
        String readKey = sample.tenant() + ":" + sample.technicalKey();
        if (!reads.containsKey(readKey)) reads.put(readKey, inbox.read(sample));
        fact = reads.get(readKey);
        if (fact != null) {
          if (sample.status() == 202 && fact.amountCents() != sample.amountCents()) {
            issues.add("accepted_amount_mismatch");
            throw new IllegalArgumentException("Accepted amount differs from requested amount");
          }
          if (!fact.tenant().equals(sample.tenant())
              || fact.receivedAt().isAfter(cut)
              || sample.canonicalId() != null && !sample.canonicalId().equals(fact.canonicalId())) {
            throw new IllegalArgumentException("Contradictory Inbox identity or timestamp");
          }
          InboxFact previous = unique.putIfAbsent(fact.canonicalId(), fact);
          if (previous != null && !previous.equals(fact))
            throw new IllegalArgumentException("Conflicting Inbox snapshots");
        }
      } catch (Exception unavailable) {
        fact = null;
        inboxComplete = false;
        issues.add("inbox_read_unavailable_or_inconsistent");
        sourceAvailable = false;
      }
      attempts.add(
          new SreReportMain.HttpRow(
              sample.id(),
              fact == null ? sample.canonicalId() : fact.canonicalId(),
              sample.startedAt().toString(),
              sample.elapsedMillis(),
              sample.status(),
              sample.eligible(),
              sample.exclusion(),
              sample.responseKind()));
    }
    List<SreReportMain.InboxRow> acceptances = new ArrayList<>();
    List<SreReportMain.DecisionRow> decisions = new ArrayList<>();
    for (var fact : unique.values()) {
      WorkerState state = states.get(fact.canonicalId());
      String lane = fact.lane();
      if (state != null) {
        if (!lane.equals("unrouted") && !lane.equals(state.lane())) {
          inboxComplete = false;
          issues.add("lane_conflict");
        }
        lane = state.lane();
        if (state.status().equals("CONFIRMED") || state.status().equals("FAILED")) {
          if (state.committedAt() == null || Instant.parse(state.committedAt()).isAfter(cut)) {
            workerComplete = false;
            issues.add("terminal_commit_timestamp_missing_or_invalid");
          } else {
            // FAILED has no typed business reason today; never promote it to a good business
            // rejection.
            decisions.add(
                new SreReportMain.DecisionRow(
                    fact.canonicalId(),
                    state.committedAt(),
                    state.status().equals("CONFIRMED") ? "confirmed" : "technical_failure"));
          }
        }
      }
      acceptances.add(
          new SreReportMain.InboxRow(
              fact.canonicalId(), fact.receivedAt().toString(), lane, fact.amountCents()));
    }
    return new Normalized(
        new SreReportMain.Input(
            1,
            new SreReportMain.Coverage(clientComplete, inboxComplete, workerComplete),
            List.copyOf(attempts),
            List.copyOf(acceptances),
            List.copyOf(decisions)),
        List.copyOf(issues.stream().distinct().toList()));
  }
}
