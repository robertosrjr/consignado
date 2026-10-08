package br.com.consignado.platform.shared.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PayloadEnvelopeTest {

  private static final byte[] KEY_A = new byte[32];
  private static final byte[] KEY_B = new byte[32];

  static {
    for (int i = 0; i < 32; i++) {
      KEY_A[i] = (byte) i;
      KEY_B[i] = (byte) (i + 100);
    }
  }

  private final PayloadEnvelope envelope = new PayloadEnvelope(new LocalDataKeyProvider(KEY_A));

  @Test
  void should_round_trip_a_payload() {
    byte[] plaintext = "{\"cpf\":\"52998224725\"}".getBytes(StandardCharsets.UTF_8);

    assertThat(envelope.unprotect(envelope.protect(plaintext))).isEqualTo(plaintext);
  }

  @Test
  void should_not_expose_the_plaintext_in_the_envelope() {
    String protectedText = envelope.protect("52998224725".getBytes(StandardCharsets.UTF_8));

    assertThat(protectedText).startsWith("v1.").doesNotContain("52998224725");
  }

  @Test
  void should_produce_different_envelopes_for_the_same_plaintext() {
    byte[] plaintext = "mesmo conteudo".getBytes(StandardCharsets.UTF_8);

    assertThat(envelope.protect(plaintext)).isNotEqualTo(envelope.protect(plaintext));
  }

  @Test
  void should_detect_tampering_of_the_ciphertext() {
    String protectedText = envelope.protect("conteudo".getBytes(StandardCharsets.UTF_8));
    String[] parts = protectedText.split("\\.");
    byte[] cipher = Base64.getUrlDecoder().decode(parts[3]);
    cipher[0] ^= 0x01;
    String tampered =
        String.join(
            ".",
            parts[0],
            parts[1],
            parts[2],
            Base64.getUrlEncoder().withoutPadding().encodeToString(cipher));

    assertThatThrownBy(() -> envelope.unprotect(tampered))
        .isInstanceOf(PayloadProtectionException.class);
  }

  @Test
  void should_fail_with_a_different_key() {
    String protectedText = envelope.protect("conteudo".getBytes(StandardCharsets.UTF_8));
    PayloadEnvelope other = new PayloadEnvelope(new LocalDataKeyProvider(KEY_B));

    assertThatThrownBy(() -> other.unprotect(protectedText))
        .isInstanceOf(PayloadProtectionException.class);
  }

  @Test
  void should_reject_malformed_and_oversized_envelopes() {
    assertThatThrownBy(() -> envelope.unprotect(null))
        .isInstanceOf(PayloadProtectionException.class);
    assertThatThrownBy(() -> envelope.unprotect("v2.a.b.c"))
        .isInstanceOf(PayloadProtectionException.class);
    assertThatThrownBy(() -> envelope.unprotect("v1.a.b"))
        .isInstanceOf(PayloadProtectionException.class);
    assertThatThrownBy(() -> envelope.unprotect("v1..AAAA.AAAA"))
        .isInstanceOf(PayloadProtectionException.class);
    assertThatThrownBy(() -> envelope.unprotect("x".repeat(PayloadEnvelope.MAX_ENVELOPE_CHARS + 1)))
        .isInstanceOf(PayloadProtectionException.class);
  }

  @Test
  void should_reject_null_and_oversized_plaintext() {
    assertThatThrownBy(() -> envelope.protect(null)).isInstanceOf(PayloadProtectionException.class);
    assertThatThrownBy(() -> envelope.protect(new byte[PayloadEnvelope.MAX_PLAINTEXT_BYTES + 1]))
        .isInstanceOf(PayloadProtectionException.class);
  }

  @Test
  void should_not_leak_the_content_in_error_messages() {
    assertThatThrownBy(() -> envelope.unprotect("v1.a.b.c")).hasMessageNotContaining("v1.a.b.c");
  }
}
