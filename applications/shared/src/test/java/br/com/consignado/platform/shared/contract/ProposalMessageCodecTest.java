package br.com.consignado.platform.shared.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;
import org.junit.jupiter.api.Test;

class ProposalMessageCodecTest {

  private static final String JSON =
      "{\"schemaVersion\":1,\"key\":\"e3b0c442-98fc-11ee-b9d1-0242ac120002\","
          + "\"tenant\":\"parceiro-01\",\"lane\":\"FAST\",\"priorityScore\":80}";

  @Test
  void should_serialize_to_the_flat_wire_format() {
    ProposalMessage message =
        ProposalMessage.of(
            IdempotencyKey.parse("e3b0c442-98fc-11ee-b9d1-0242ac120002"),
            new TenantId("parceiro-01"),
            Lane.FAST,
            80);

    assertThat(ProposalMessageCodec.toJson(message)).isEqualTo(JSON);
  }

  @Test
  void should_deserialize_the_wire_format() {
    ProposalMessage message = ProposalMessageCodec.fromJson(JSON);

    assertThat(message.key().toString()).isEqualTo("e3b0c442-98fc-11ee-b9d1-0242ac120002");
    assertThat(message.tenant().value()).isEqualTo("parceiro-01");
    assertThat(message.lane()).isEqualTo(Lane.FAST);
    assertThat(message.priorityScore()).isEqualTo(80);
  }

  @Test
  void should_ignore_unknown_fields_for_forward_compatibility() {
    String withExtra = JSON.replace("}", ",\"futureField\":\"x\"}");

    assertThat(ProposalMessageCodec.fromJson(withExtra).lane()).isEqualTo(Lane.FAST);
  }

  @Test
  void should_reject_unknown_schema_version_without_leaking_content() {
    String v2 = JSON.replace("\"schemaVersion\":1", "\"schemaVersion\":2");

    assertThatThrownBy(() -> ProposalMessageCodec.fromJson(v2))
        .isInstanceOf(MessageContractException.class)
        .hasMessageNotContaining("parceiro-01");
  }

  @Test
  void should_reject_malformed_json_and_invalid_values() {
    assertThatThrownBy(() -> ProposalMessageCodec.fromJson("{"))
        .isInstanceOf(MessageContractException.class);
    assertThatThrownBy(() -> ProposalMessageCodec.fromJson(JSON.replace("FAST", "TURBO")))
        .isInstanceOf(MessageContractException.class);
    assertThatThrownBy(() -> ProposalMessageCodec.fromJson(JSON.replace("parceiro-01", "bad id")))
        .isInstanceOf(MessageContractException.class);
  }
}
