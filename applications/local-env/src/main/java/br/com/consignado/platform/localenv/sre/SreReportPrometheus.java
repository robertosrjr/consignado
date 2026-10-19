package br.com.consignado.platform.localenv.sre;

import java.time.Instant;

/** Gauges for the latest supplied experiment, never counters or operational live SLIs. */
public final class SreReportPrometheus {
  private SreReportPrometheus() {}

  public static String render(SreReport.Policy policy, SreReport.Result result) {
    StringBuilder text = new StringBuilder();
    gauge(
        text,
        "report_status",
        "0 pass, 1 fail, 2 incomplete, 3 observing, 4 no events",
        switch (result.status()) {
          case "PASS" -> 0;
          case "FAIL" -> 1;
          case "OBSERVING" -> 3;
          case "NO_EVENTS" -> 4;
          default -> 2;
        });
    boolean complete = !result.status().equals("INCOMPLETE");
    gauge(
        text,
        "coverage_complete",
        "Evidence declared complete and structurally consistent",
        complete ? 1 : 0);
    gauge(
        text,
        "observed_at_seconds",
        "UTC observation cut of the supplied evidence",
        Instant.parse(result.asOf()).getEpochSecond());
    gauge(
        text,
        "cohort_start_seconds",
        "UTC experiment cohort start inclusive",
        policy.start().getEpochSecond());
    gauge(
        text,
        "cohort_end_seconds",
        "UTC experiment cohort end exclusive",
        policy.end().getEpochSecond());
    if (!complete) return text.toString();
    family(text, "indicator_good", "Good events in the supplied experiment cohort");
    family(text, "indicator_bad", "Bad events in the supplied experiment cohort");
    family(text, "indicator_eligible", "Eligible events in the supplied experiment cohort");
    family(text, "indicator_target", "Experimental target, not an external SLA");
    family(text, "indicator_ratio", "Good divided by eligible, absent with zero denominator");
    family(text, "indicator_budget_consumed_ratio", "Budget consumed, one means 100 percent");
    result
        .indicators()
        .forEach(
            (sli, value) -> {
              labeled(text, "indicator_good", sli, value.good());
              labeled(text, "indicator_bad", sli, value.bad());
              labeled(text, "indicator_eligible", sli, value.eligible());
              labeled(text, "indicator_target", sli, value.target());
              if (value.ratio() != null) {
                labeled(text, "indicator_ratio", sli, value.ratio());
                labeled(text, "indicator_budget_consumed_ratio", sli, value.budgetConsumed());
              }
            });
    gauge(text, "accepted", "Unique accepted proposals in the experiment", result.accepted());
    gauge(text, "confirmed", "Confirmed proposals including late decisions", result.confirmed());
    gauge(
        text,
        "rejected",
        "Business rejected proposals including late decisions",
        result.rejected());
    gauge(text, "open", "Unresolved proposals including technical failure or DLQ", result.open());
    gauge(text, "received_cents", "Accepted amount in integer BRL cents", result.receivedCents());
    gauge(
        text, "confirmed_cents", "Confirmed amount in integer BRL cents", result.confirmedCents());
    gauge(
        text,
        "rejected_cents",
        "Business rejected amount in integer BRL cents",
        result.rejectedCents());
    gauge(text, "open_cents", "Unresolved amount in integer BRL cents", result.openCents());
    gauge(
        text,
        "overdue_open",
        "Unresolved proposals whose deadline has expired",
        result.overdueOpen());
    gauge(text, "not_yet_due", "Proposals whose deadline has not expired", result.notYetDue());
    return text.toString();
  }

  public static String incomplete() {
    StringBuilder text = new StringBuilder();
    gauge(text, "report_status", "0 pass, 1 fail, 2 incomplete, 3 observing, 4 no events", 2);
    gauge(text, "coverage_complete", "Evidence declared complete and structurally consistent", 0);
    return text.toString();
  }

  private static void family(StringBuilder text, String name, String help) {
    text.append("# HELP sre_experiment_")
        .append(name)
        .append(' ')
        .append(help)
        .append('\n')
        .append("# TYPE sre_experiment_")
        .append(name)
        .append(" gauge\n");
  }

  private static void gauge(StringBuilder text, String name, String help, long value) {
    family(text, name, help);
    text.append("sre_experiment_").append(name).append(' ').append(value).append('\n');
  }

  private static void labeled(StringBuilder text, String name, String sli, Number value) {
    // SLI is a fixed internal enum-like key, never an identifier supplied by a caller.
    text.append("sre_experiment_")
        .append(name)
        .append("{sli=\"")
        .append(sli)
        .append("\"} ")
        .append(value)
        .append('\n');
  }
}
