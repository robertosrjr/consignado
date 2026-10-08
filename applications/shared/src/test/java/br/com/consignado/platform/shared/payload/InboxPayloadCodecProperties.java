package br.com.consignado.platform.shared.payload;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.crypto.LocalDataKeyProvider;
import br.com.consignado.platform.shared.crypto.PayloadEnvelope;
import br.com.consignado.platform.shared.testkit.DomainArbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Round-trip do payload do inbox, isolado e combinado com a criptografia de envelope (PBT-02). */
class InboxPayloadCodecProperties {

  private final PayloadEnvelope envelope =
      new PayloadEnvelope(new LocalDataKeyProvider(new byte[32]));

  @Provide
  Arbitrary<InboxPayload> payloads() {
    return DomainArbitraries.inboxPayload();
  }

  @Property
  void codec_round_trip_is_identity(@ForAll("payloads") InboxPayload payload) {
    assertThat(InboxPayloadCodec.fromBytes(InboxPayloadCodec.toBytes(payload))).isEqualTo(payload);
  }

  @Property
  void protect_unprotect_round_trip_preserves_the_payload(
      @ForAll("payloads") InboxPayload payload) {
    String protectedText = envelope.protect(InboxPayloadCodec.toBytes(payload));

    assertThat(InboxPayloadCodec.fromBytes(envelope.unprotect(protectedText))).isEqualTo(payload);
    assertThat(protectedText).doesNotContain(payload.cpf().digits());
  }
}
