package br.com.consignado.platform.localenv.sre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class JtlCaptureTest {
  private static final String KEY = "11111111-1111-4111-8111-111111111111";
  private static final String HEADER =
      "timeStamp,elapsed,label,responseCode,key,tenant,sre_canonical,sre_response_kind,sre_eligible,sre_exclusion,responseMessage,success,amount\r\n";

  private static String row(String label, String code, String canonical, String kind) {
    return "1792242000000,250,"
        + label
        + ","
        + code
        + ","
        + KEY
        + ",banco-a,"
        + canonical
        + ","
        + kind
        + ",true,none,OK,false,100.00\r\n";
  }

  @Test
  void readsOnlyProposalSamplesAndDoesNotConfuseDurationAssertionWithHttpFailure() {
    var samples =
        JtlCapture.read(
            "\uFEFF"
                + HEADER
                + row("GET /token", "200", "", "NONE")
                + row("POST negocio (nova)", "202", KEY, "RECEIVED"));
    assertThat(samples).hasSize(1);
    assertThat(samples.getFirst().status()).isEqualTo(202);
    assertThat(samples.getFirst().elapsedMillis()).isEqualTo(250);
    assertThat(samples.getFirst().startedAt().toEpochMilli()).isEqualTo(1792242000000L);
    assertThat(samples.getFirst().responseKind()).isEqualTo("RECEIVED");
  }

  @Test
  void connectionFailureHasNoInventedCanonicalIdentity() {
    var samples =
        JtlCapture.read(HEADER + row("POST test", "Non HTTP response code: timeout", "", "NONE"));
    assertThat(samples.getFirst().status()).isZero();
    assertThat(samples.getFirst().canonicalId()).isNull();
  }

  @Test
  void supportsQuotedCommasNewlinesAndEscapedQuotes() {
    assertThat(JtlCapture.csv("a,b\r\n\"x,\"\"y\"\"\nz\",ok\r\n").get(1))
        .containsExactly("x,\"y\"\nz", "ok");
  }

  @Test
  void rejectsTruncatedRowsMissingCaptureFieldsAndMalformedIdentity() {
    assertThatIllegalArgumentException().isThrownBy(() -> JtlCapture.read(HEADER + "1,2,POST\n"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> JtlCapture.read("timeStamp,elapsed,label\n"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> JtlCapture.read(HEADER + row("POST", "202", "not-a-key", "RECEIVED")));
    assertThatIllegalArgumentException().isThrownBy(() -> JtlCapture.csv("a\n\"unfinished"));
  }
}
