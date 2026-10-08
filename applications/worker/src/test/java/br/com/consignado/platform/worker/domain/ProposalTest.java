package br.com.consignado.platform.worker.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProposalTest {

  private static final Instant T0 = Instant.parse("2026-10-08T12:00:00Z");
  private static final Instant T1 = T0.plusSeconds(1);

  private static Proposal pending() {
    return Proposal.pending(
        IdempotencyKey.parse("e3b0c442-98fc-11ee-b9d1-0242ac120002"),
        new TenantId("banco-a"),
        Lane.FAST,
        T0);
  }

  @Test
  void should_start_pending_with_no_attempts() {
    Proposal proposal = pending();

    assertThat(proposal.status()).isEqualTo(ProposalStatus.PENDING);
    assertThat(proposal.attempts()).isZero();
    assertThat(proposal.isFinal()).isFalse();
  }

  @Test
  void should_move_pending_to_sent_to_legacy_counting_the_attempt() {
    Proposal sent = pending().markSentToLegacy(T1);

    assertThat(sent.status()).isEqualTo(ProposalStatus.SENT_TO_LEGACY);
    assertThat(sent.attempts()).isEqualTo(1);
    assertThat(sent.updatedAt()).isEqualTo(T1);
    assertThat(sent.createdAt()).isEqualTo(T0);
  }

  @Test
  void should_allow_resending_with_the_same_key_and_count_each_attempt() {
    Proposal resent = pending().markSentToLegacy(T0).markSentToLegacy(T1);

    assertThat(resent.status()).isEqualTo(ProposalStatus.SENT_TO_LEGACY);
    assertThat(resent.attempts()).isEqualTo(2);
  }

  @Test
  void should_confirm_a_sent_proposal_and_keep_the_averbacao_id() {
    Proposal confirmed = pending().markSentToLegacy(T0).confirm("av-1", T1);

    assertThat(confirmed.status()).isEqualTo(ProposalStatus.CONFIRMED);
    assertThat(confirmed.averbacaoId()).isEqualTo("av-1");
    assertThat(confirmed.isFinal()).isTrue();
  }

  @Test
  void should_fail_a_sent_proposal_with_a_bounded_reason() {
    Proposal failed = pending().markSentToLegacy(T0).fail("x".repeat(1000), T1);

    assertThat(failed.status()).isEqualTo(ProposalStatus.FAILED);
    assertThat(failed.failureReason()).hasSize(Proposal.MAX_REASON_LENGTH);
    assertThat(pending().markSentToLegacy(T0).fail(null, T1).failureReason())
        .isEqualTo("unspecified");
  }

  @Test
  void should_reject_transitions_that_skip_sending() {
    assertThatThrownBy(() -> pending().confirm("av", T1))
        .isInstanceOf(IllegalStateTransitionException.class);
    assertThatThrownBy(() -> pending().fail("r", T1))
        .isInstanceOf(IllegalStateTransitionException.class);
  }

  @Test
  void should_keep_final_states_absorbing() {
    Proposal confirmed = pending().markSentToLegacy(T0).confirm("av", T1);
    Proposal failed = pending().markSentToLegacy(T0).fail("r", T1);

    for (Proposal terminal : new Proposal[] {confirmed, failed}) {
      assertThatThrownBy(() -> terminal.markSentToLegacy(T1))
          .isInstanceOf(IllegalStateTransitionException.class);
      assertThatThrownBy(() -> terminal.confirm("av", T1))
          .isInstanceOf(IllegalStateTransitionException.class);
      assertThatThrownBy(() -> terminal.fail("r", T1))
          .isInstanceOf(IllegalStateTransitionException.class);
    }
  }

  @Test
  void should_not_hold_any_personal_data_in_to_string() {
    assertThat(pending().toString()).doesNotContain("cpf").doesNotContain("amount");
  }

  @Test
  void should_reject_missing_required_fields() {
    assertThatThrownBy(() -> Proposal.pending(null, new TenantId("a"), Lane.FAST, T0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
