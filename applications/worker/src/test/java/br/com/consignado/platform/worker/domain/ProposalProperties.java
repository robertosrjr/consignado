package br.com.consignado.platform.worker.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/**
 * Teste baseado em modelo da maquina de estados (PBT-06, stateful): para qualquer sequencia de
 * acoes, o agregado e o modelo concordam sobre a validade de cada transicao, estados terminais sao
 * absorventes e o contador de tentativas conta somente os envios aceitos.
 */
class ProposalProperties {

  enum Action {
    SEND,
    CONFIRM,
    FAIL
  }

  private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

  @Property
  void aggregate_agrees_with_the_reference_model_for_any_action_sequence(
      @ForAll List<Action> actions) {
    Proposal proposal =
        Proposal.pending(
            new IdempotencyKey(new UUID(1, 2)), new TenantId("banco-a"), Lane.STANDARD, NOW);
    ProposalStatus model = ProposalStatus.PENDING;
    int sends = 0;

    for (Action action : actions) {
      boolean allowed =
          switch (action) {
            case SEND -> model == ProposalStatus.PENDING || model == ProposalStatus.SENT_TO_LEGACY;
            case CONFIRM, FAIL -> model == ProposalStatus.SENT_TO_LEGACY;
          };
      try {
        proposal =
            switch (action) {
              case SEND -> proposal.markSentToLegacy(NOW);
              case CONFIRM -> proposal.confirm("av", NOW);
              case FAIL -> proposal.fail("motivo", NOW);
            };
        assertThat(allowed).as("transicao aceita deveria ser valida").isTrue();
        model =
            switch (action) {
              case SEND -> ProposalStatus.SENT_TO_LEGACY;
              case CONFIRM -> ProposalStatus.CONFIRMED;
              case FAIL -> ProposalStatus.FAILED;
            };
        if (action == Action.SEND) {
          sends++;
        }
      } catch (IllegalStateTransitionException rejected) {
        assertThat(allowed).as("transicao recusada deveria ser invalida").isFalse();
      }
      assertThat(proposal.status()).isEqualTo(model);
    }

    assertThat(proposal.attempts()).isEqualTo(sends);
    if (model.isFinal()) {
      assertThat(proposal.isFinal()).isTrue();
    }
  }
}
