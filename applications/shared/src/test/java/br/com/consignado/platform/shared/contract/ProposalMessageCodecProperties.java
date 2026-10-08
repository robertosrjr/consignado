package br.com.consignado.platform.shared.contract;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.testkit.DomainArbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Round-trip JSON de {@link ProposalMessage} (PBT-02) e propriedades do contrato (PBT-03). */
class ProposalMessageCodecProperties {

  @Provide
  Arbitrary<ProposalMessage> messages() {
    return DomainArbitraries.proposalMessage();
  }

  @Property
  void json_round_trip_is_identity(@ForAll("messages") ProposalMessage message) {
    assertThat(ProposalMessageCodec.fromJson(ProposalMessageCodec.toJson(message)))
        .isEqualTo(message);
  }

  @Property
  void serialized_message_never_contains_pii_fields(@ForAll("messages") ProposalMessage message) {
    String json = ProposalMessageCodec.toJson(message);

    // verifica nomes de campo (aspas e dois-pontos): o tenant gerado pode valer "cpf" legitimamente
    assertThat(json)
        .doesNotContain("\"cpf\":")
        .doesNotContain("\"amount\":")
        .doesNotContain("\"payload\":");
  }

  @Property
  void serialization_is_deterministic(@ForAll("messages") ProposalMessage message) {
    assertThat(ProposalMessageCodec.toJson(message))
        .isEqualTo(ProposalMessageCodec.toJson(message));
  }
}
