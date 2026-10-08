package br.com.consignado.platform.shared.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;
import org.junit.jupiter.api.Test;

class ProposalMessageTest {

  private static final IdempotencyKey KEY =
      IdempotencyKey.parse("e3b0c442-98fc-11ee-b9d1-0242ac120002");
  private static final TenantId TENANT = new TenantId("parceiro-01");

  @Test
  void should_create_message_with_current_schema_version() {
    ProposalMessage message = ProposalMessage.of(KEY, TENANT, Lane.FAST, 80);

    assertThat(message.schemaVersion()).isEqualTo(ProposalMessage.CURRENT_SCHEMA_VERSION);
  }

  @Test
  void should_reject_unsupported_schema_version() {
    assertThatThrownBy(() -> new ProposalMessage(2, KEY, TENANT, Lane.FAST, 10))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_priority_score_out_of_range() {
    assertThatThrownBy(() -> ProposalMessage.of(KEY, TENANT, Lane.FAST, -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ProposalMessage.of(KEY, TENANT, Lane.FAST, 101))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_missing_fields() {
    assertThatThrownBy(() -> ProposalMessage.of(null, TENANT, Lane.FAST, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ProposalMessage.of(KEY, null, Lane.FAST, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ProposalMessage.of(KEY, TENANT, null, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
