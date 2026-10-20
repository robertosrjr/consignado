package br.com.consignado.platform.localenv.sre;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict CSV reader for JMeter's local, unaggregated proposal samples, including quoted newlines.
 */
public final class JtlCapture {
  private JtlCapture() {}

  public record Sample(
      String id,
      String technicalKey,
      String tenant,
      String canonicalId,
      Instant startedAt,
      long elapsedMillis,
      int status,
      boolean eligible,
      String exclusion,
      String responseKind,
      long amountCents) {}

  public static List<Sample> read(String csv) {
    List<List<String>> rows = csv(csv.startsWith("\uFEFF") ? csv.substring(1) : csv);
    if (rows.isEmpty()) throw new IllegalArgumentException("Missing JTL header");
    List<String> header = rows.getFirst();
    if (new HashSet<>(header).size() != header.size()
        || !header.containsAll(
            List.of(
                "timeStamp",
                "elapsed",
                "label",
                "responseCode",
                "key",
                "tenant",
                "amount",
                "sre_canonical",
                "sre_response_kind",
                "sre_eligible",
                "sre_exclusion"))) throw new IllegalArgumentException("Unsupported JTL header");
    List<Sample> samples = new ArrayList<>();
    for (int i = 1; i < rows.size(); i++) {
      List<String> row = rows.get(i);
      if (row.size() == 1 && row.getFirst().isEmpty()) continue;
      if (row.size() != header.size()) throw new IllegalArgumentException("Truncated JTL row");
      Map<String, String> values = new LinkedHashMap<>();
      for (int j = 0; j < header.size(); j++) values.put(header.get(j), row.get(j));
      if (!values.get("label").startsWith("POST")) continue;
      String technicalKey = uuid(values.get("key"));
      String tenant = values.get("tenant");
      if (!tenant.matches("[a-zA-Z0-9._-]{1,100}"))
        throw new IllegalArgumentException("Invalid tenant");
      String canonical = values.get("sre_canonical");
      canonical = canonical.isEmpty() ? null : uuid(canonical);
      String eligible = values.get("sre_eligible");
      if (!eligible.equals("true") && !eligible.equals("false"))
        throw new IllegalArgumentException("Missing eligibility");
      int status =
          values.get("responseCode").matches("[1-5][0-9]{2}")
              ? Integer.parseInt(values.get("responseCode"))
              : 0;
      long elapsed = Long.parseLong(values.get("elapsed"));
      Instant start = Instant.ofEpochMilli(Long.parseLong(values.get("timeStamp")));
      var sample =
          new Sample(
              "sample-" + i,
              technicalKey,
              tenant,
              canonical,
              start,
              elapsed,
              status,
              Boolean.parseBoolean(eligible),
              values.get("sre_exclusion"),
              values.get("sre_response_kind"),
              new java.math.BigDecimal(values.get("amount")).movePointRight(2).longValueExact());
      if (sample.amountCents() <= 0) throw new IllegalArgumentException("Invalid requested amount");
      // Apply the same validation as the offline contract before reading backend sources.
      new SreReport.Attempt(
          sample.id(),
          canonical,
          start,
          elapsed,
          status,
          sample.eligible(),
          sample.exclusion(),
          sample.responseKind());
      samples.add(sample);
    }
    return List.copyOf(samples);
  }

  private static String uuid(String value) {
    if (!value.matches(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
      throw new IllegalArgumentException("Invalid technical identity");
    return java.util.UUID.fromString(value).toString();
  }

  static List<List<String>> csv(String text) {
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false, closed = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
            field.append('"');
            i++;
          } else {
            quoted = false;
            closed = true;
          }
        } else field.append(c);
      } else if (c == ',' || c == '\n' || c == '\r') {
        row.add(field.toString());
        field.setLength(0);
        closed = false;
        if (c != ',') {
          rows.add(List.copyOf(row));
          row.clear();
          if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
        }
      } else if (c == '"' && field.isEmpty() && !closed) quoted = true;
      else {
        if (closed || c == '"') throw new IllegalArgumentException("Invalid CSV quoting");
        field.append(c);
      }
    }
    if (quoted) throw new IllegalArgumentException("Unclosed CSV field");
    if (!row.isEmpty() || !field.isEmpty() || closed) {
      row.add(field.toString());
      rows.add(List.copyOf(row));
    }
    return rows;
  }
}
