package br.com.consignado.platform.shared.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;

/** Round-trip e nao determinismo da criptografia de envelope (PBT-02, PBT-03). */
class PayloadEnvelopeProperties {

  private static final byte[] KEY = new byte[32];

  static {
    for (int i = 0; i < 32; i++) {
      KEY[i] = (byte) (31 - i);
    }
  }

  private final PayloadEnvelope envelope = new PayloadEnvelope(new LocalDataKeyProvider(KEY));

  @Property
  void unprotect_of_protect_is_identity(@ForAll @Size(max = 4096) byte[] plaintext) {
    assertThat(envelope.unprotect(envelope.protect(plaintext))).isEqualTo(plaintext);
  }

  @Property
  void protecting_twice_never_yields_the_same_envelope(@ForAll @Size(max = 512) byte[] plaintext) {
    assertThat(envelope.protect(plaintext)).isNotEqualTo(envelope.protect(plaintext));
  }

  @Property
  void envelope_is_always_four_dot_separated_url_safe_fields(
      @ForAll @Size(max = 512) byte[] plaintext) {
    String text = envelope.protect(plaintext);

    assertThat(text.split("\\.", -1)).hasSize(4);
    assertThat(text).matches("[A-Za-z0-9_.-]+");
  }
}
