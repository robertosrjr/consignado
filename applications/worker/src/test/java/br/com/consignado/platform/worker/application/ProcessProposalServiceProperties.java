package br.com.consignado.platform.worker.application;

import static br.com.consignado.platform.worker.application.WorkerFixtures.KEY;
import static br.com.consignado.platform.worker.application.WorkerFixtures.inboxRecord;
import static br.com.consignado.platform.worker.application.WorkerFixtures.message;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.worker.application.WorkerFixtures.FixedClock;
import br.com.consignado.platform.worker.application.WorkerFixtures.ScriptedLegacy;
import br.com.consignado.platform.worker.application.port.in.ProcessingOutcome;
import br.com.consignado.platform.worker.application.usecase.ProcessProposalService;
import br.com.consignado.platform.worker.domain.LegacyResult;
import br.com.consignado.platform.worker.domain.Proposal;
import br.com.consignado.platform.worker.domain.ProposalEvent;
import br.com.consignado.platform.worker.domain.ProposalStatus;
import br.com.consignado.platform.worker.infrastructure.adapter.out.inbox.InMemoryInboxReadAdapter;
import br.com.consignado.platform.worker.infrastructure.adapter.out.persistence.InMemoryProposalRepository;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;

/**
 * Idempotencia do processamento (PBT-04): para qualquer roteiro de respostas do legado e qualquer
 * numero de entregas da mesma mensagem, nunca ha chamada ao legado depois de um estado terminal, ha
 * no maximo um evento terminal e o estado final e consistente com o historico.
 */
class ProcessProposalServiceProperties {

  enum Reply {
    TRANSIENT,
    CONFIRMED,
    REJECTED
  }

  private static LegacyResult result(Reply reply) {
    return switch (reply) {
      case TRANSIENT -> new LegacyResult.TransientFailure();
      case CONFIRMED -> new LegacyResult.Confirmed("av-1");
      case REJECTED -> new LegacyResult.Rejected("recusada");
    };
  }

  @Property
  void redelivery_never_calls_the_legacy_after_a_final_state_and_keeps_one_terminal_event(
      @ForAll @Size(min = 1, max = 20) List<Reply> replies) {
    FixedClock clock = new FixedClock();
    InMemoryProposalRepository repository = new InMemoryProposalRepository(clock);
    InMemoryInboxReadAdapter inbox = new InMemoryInboxReadAdapter();
    inbox.put(inboxRecord(KEY));
    ScriptedLegacy legacy = new ScriptedLegacy();
    ProcessProposalService service = new ProcessProposalService(repository, inbox, legacy, clock);

    int callsBeforeFinal = 0;
    boolean isFinal = false;
    for (Reply reply : replies) {
      legacy.script.add(result(reply));
      int before = legacy.calls.size();
      ProcessingOutcome outcome = service.process(message());

      if (isFinal) {
        assertThat(legacy.calls.size()).as("sem chamadas apos estado terminal").isEqualTo(before);
        assertThat(outcome).isEqualTo(ProcessingOutcome.COMPLETED);
        legacy.script.clear();
      } else {
        callsBeforeFinal++;
        assertThat(legacy.calls.size()).isEqualTo(before + 1);
        isFinal = reply != Reply.TRANSIENT;
        assertThat(outcome)
            .isEqualTo(
                reply == Reply.TRANSIENT ? ProcessingOutcome.RETRY : ProcessingOutcome.COMPLETED);
      }
    }

    Proposal proposal = repository.find(KEY).orElseThrow();
    long terminalEvents =
        repository.history(KEY).stream()
            .filter(
                e ->
                    e.type().equals(ProposalEvent.CONFIRMED)
                        || e.type().equals(ProposalEvent.FAILED))
            .count();
    assertThat(terminalEvents).isEqualTo(isFinal ? 1 : 0);
    assertThat(proposal.attempts()).isEqualTo(callsBeforeFinal);
    assertThat(legacy.calls).hasSize(callsBeforeFinal);
    assertThat(proposal.status().isFinal()).isEqualTo(isFinal);
    if (proposal.status() == ProposalStatus.CONFIRMED) {
      assertThat(repository.history(KEY).stream().map(ProposalEvent::type))
          .contains(ProposalEvent.CONFIRMED)
          .doesNotContain(ProposalEvent.FAILED);
    }
  }

  @Property
  void every_call_to_the_legacy_carries_the_same_idempotency_key(
      @ForAll @Size(min = 1, max = 12) List<Reply> replies) {
    FixedClock clock = new FixedClock();
    InMemoryProposalRepository repository = new InMemoryProposalRepository(clock);
    InMemoryInboxReadAdapter inbox = new InMemoryInboxReadAdapter();
    inbox.put(inboxRecord(KEY));
    ScriptedLegacy legacy = new ScriptedLegacy();
    ProcessProposalService service = new ProcessProposalService(repository, inbox, legacy, clock);

    replies.forEach(reply -> legacy.script.add(result(reply)));
    for (int i = 0; i < replies.size(); i++) {
      service.process(message());
    }

    assertThat(legacy.calls).allMatch(call -> call.idempotencyKey().equals(KEY));
  }
}
