package br.com.consignado.platform.worker.application;

import static br.com.consignado.platform.worker.application.WorkerFixtures.KEY;
import static br.com.consignado.platform.worker.application.WorkerFixtures.inboxRecord;
import static br.com.consignado.platform.worker.application.WorkerFixtures.message;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.worker.application.WorkerFixtures.FixedClock;
import br.com.consignado.platform.worker.application.WorkerFixtures.ScriptedLegacy;
import br.com.consignado.platform.worker.application.port.in.ProcessingOutcome;
import br.com.consignado.platform.worker.application.port.out.ProposalSeed;
import br.com.consignado.platform.worker.application.usecase.ProcessProposalService;
import br.com.consignado.platform.worker.domain.LegacyResult;
import br.com.consignado.platform.worker.domain.Proposal;
import br.com.consignado.platform.worker.domain.ProposalEvent;
import br.com.consignado.platform.worker.domain.ProposalStatus;
import br.com.consignado.platform.worker.infrastructure.adapter.out.inbox.InMemoryInboxReadAdapter;
import br.com.consignado.platform.worker.infrastructure.adapter.out.persistence.InMemoryProposalRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessProposalServiceTest {

  private final FixedClock clock = new FixedClock();
  private final InMemoryProposalRepository repository = new InMemoryProposalRepository(clock);
  private final InMemoryInboxReadAdapter inbox = new InMemoryInboxReadAdapter();
  private final ScriptedLegacy legacy = new ScriptedLegacy();
  private final ProcessProposalService service =
      new ProcessProposalService(repository, inbox, legacy, clock);

  private List<String> eventTypes() {
    return repository.history(KEY).stream().map(ProposalEvent::type).toList();
  }

  @Test
  void should_confirm_the_proposal_and_record_the_full_history() {
    inbox.put(inboxRecord(KEY));
    legacy.script.add(new LegacyResult.Confirmed("av-77"));

    ProcessingOutcome outcome = service.process(message());

    assertThat(outcome).isEqualTo(ProcessingOutcome.COMPLETED);
    Proposal proposal = repository.find(KEY).orElseThrow();
    assertThat(proposal.status()).isEqualTo(ProposalStatus.CONFIRMED);
    assertThat(proposal.averbacaoId()).isEqualTo("av-77");
    assertThat(eventTypes())
        .containsExactly(
            ProposalEvent.CREATED, ProposalEvent.SENT_TO_LEGACY, ProposalEvent.CONFIRMED);
  }

  @Test
  void should_send_the_idempotency_key_and_the_decrypted_data_to_the_legacy() {
    inbox.put(inboxRecord(KEY));

    service.process(message());

    assertThat(legacy.calls).hasSize(1);
    assertThat(legacy.calls.get(0).idempotencyKey()).isEqualTo(KEY);
    assertThat(legacy.calls.get(0).cpf().digits()).isEqualTo("52998224725");
    assertThat(legacy.calls.get(0).installments()).isEqualTo(12);
  }

  @Test
  void should_record_a_business_rejection_as_a_final_failed_state() {
    inbox.put(inboxRecord(KEY));
    legacy.script.add(new LegacyResult.Rejected("margem insuficiente"));

    ProcessingOutcome outcome = service.process(message());

    assertThat(outcome).isEqualTo(ProcessingOutcome.COMPLETED);
    Proposal proposal = repository.find(KEY).orElseThrow();
    assertThat(proposal.status()).isEqualTo(ProposalStatus.FAILED);
    assertThat(proposal.failureReason()).isEqualTo("margem insuficiente");
    assertThat(eventTypes()).endsWith(ProposalEvent.FAILED);
  }

  @Test
  void should_ask_for_a_retry_on_a_transient_failure_and_resend_with_the_same_key() {
    inbox.put(inboxRecord(KEY));
    legacy.script.add(new LegacyResult.TransientFailure());
    legacy.script.add(new LegacyResult.Confirmed("av-9"));

    assertThat(service.process(message())).isEqualTo(ProcessingOutcome.RETRY);
    assertThat(repository.find(KEY).orElseThrow().status())
        .isEqualTo(ProposalStatus.SENT_TO_LEGACY);

    assertThat(service.process(message())).isEqualTo(ProcessingOutcome.COMPLETED);

    Proposal proposal = repository.find(KEY).orElseThrow();
    assertThat(proposal.status()).isEqualTo(ProposalStatus.CONFIRMED);
    assertThat(proposal.attempts()).isEqualTo(2);
    assertThat(legacy.calls).extracting(c -> c.idempotencyKey()).containsExactly(KEY, KEY);
  }

  @Test
  void should_not_call_the_legacy_again_for_a_duplicate_message_after_a_final_state() {
    inbox.put(inboxRecord(KEY));
    service.process(message());

    ProcessingOutcome outcome = service.process(message());

    assertThat(outcome).isEqualTo(ProcessingOutcome.COMPLETED);
    assertThat(legacy.calls).hasSize(1);
    assertThat(eventTypes().stream().filter(ProposalEvent.CONFIRMED::equals).count()).isEqualTo(1);
  }

  @Test
  void should_resend_after_a_crash_between_marking_sent_and_saving_the_result() {
    inbox.put(inboxRecord(KEY));
    repository.createPendingIfAbsent(new ProposalSeed(KEY, WorkerFixtures.TENANT, Lane.FAST));
    Proposal pending = repository.find(KEY).orElseThrow();
    repository.save(pending.markSentToLegacy(clock.now()), List.of());

    ProcessingOutcome outcome = service.process(message());

    assertThat(outcome).isEqualTo(ProcessingOutcome.COMPLETED);
    assertThat(repository.find(KEY).orElseThrow().status()).isEqualTo(ProposalStatus.CONFIRMED);
    assertThat(repository.find(KEY).orElseThrow().attempts()).isEqualTo(2);
  }

  @Test
  void should_report_unprocessable_when_the_inbox_item_is_missing_without_calling_the_legacy() {
    ProcessingOutcome outcome = service.process(message());

    assertThat(outcome).isEqualTo(ProcessingOutcome.UNPROCESSABLE);
    assertThat(repository.find(KEY)).isEmpty();
    assertThat(legacy.calls).isEmpty();
  }

  @Test
  void should_propagate_unexpected_legacy_errors_leaving_the_proposal_marked_as_sent() {
    inbox.put(inboxRecord(KEY));
    legacy.failure = new IllegalStateException("erro inesperado");

    assertThatThrownBy(() -> service.process(message())).isInstanceOf(IllegalStateException.class);

    assertThat(repository.find(KEY).orElseThrow().status())
        .isEqualTo(ProposalStatus.SENT_TO_LEGACY);
  }

  @Test
  void should_not_leak_personal_data_into_the_event_history() {
    inbox.put(inboxRecord(KEY));
    legacy.script.add(new LegacyResult.Confirmed("av-1"));

    service.process(message());

    assertThat(repository.history(KEY).toString())
        .doesNotContain("52998224725")
        .doesNotContain("1000");
  }
}
