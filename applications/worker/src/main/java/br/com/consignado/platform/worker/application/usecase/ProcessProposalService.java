package br.com.consignado.platform.worker.application.usecase;

import br.com.consignado.platform.shared.contract.ProposalMessage;
import br.com.consignado.platform.shared.payload.InboxPayload;
import br.com.consignado.platform.worker.application.port.in.ProcessProposalUseCase;
import br.com.consignado.platform.worker.application.port.in.ProcessingOutcome;
import br.com.consignado.platform.worker.application.port.out.BusinessObserver;
import br.com.consignado.platform.worker.application.port.out.ClockPort;
import br.com.consignado.platform.worker.application.port.out.CreationResult;
import br.com.consignado.platform.worker.application.port.out.InboxReadPort;
import br.com.consignado.platform.worker.application.port.out.InboxRecord;
import br.com.consignado.platform.worker.application.port.out.LegacyCorePort;
import br.com.consignado.platform.worker.application.port.out.LegacySubmission;
import br.com.consignado.platform.worker.application.port.out.ProposalRepositoryPort;
import br.com.consignado.platform.worker.application.port.out.ProposalSeed;
import br.com.consignado.platform.worker.application.port.out.ProposalSettlement;
import br.com.consignado.platform.worker.domain.LegacyResult;
import br.com.consignado.platform.worker.domain.Proposal;
import br.com.consignado.platform.worker.domain.ProposalEvent;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Leva a proposta ate o estado final sem averbacao duplicada: cria PENDING (idempotente), marca o
 * envio, chama o legado com a mesma chave de idempotencia e grava o resultado. Reenvios (mensagem
 * duplicada ou queda no meio) usam a mesma chave, entao o legado devolve o mesmo resultado; nunca
 * ha chamada ao legado depois de um estado terminal.
 */
public class ProcessProposalService implements ProcessProposalUseCase {

  private static final Logger LOG = System.getLogger(ProcessProposalService.class.getName());

  private final ProposalRepositoryPort repository;
  private final InboxReadPort inbox;
  private final LegacyCorePort legacy;
  private final ClockPort clock;
  private final BusinessObserver business;

  public ProcessProposalService(
      ProposalRepositoryPort repository,
      InboxReadPort inbox,
      LegacyCorePort legacy,
      ClockPort clock) {
    this(repository, inbox, legacy, clock, BusinessObserver.NONE);
  }

  public ProcessProposalService(
      ProposalRepositoryPort repository,
      InboxReadPort inbox,
      LegacyCorePort legacy,
      ClockPort clock,
      BusinessObserver business) {
    this.repository = repository;
    this.inbox = inbox;
    this.legacy = legacy;
    this.clock = clock;
    this.business = business;
  }

  @Override
  public ProcessingOutcome process(ProposalMessage message) {
    InboxRecord record = inbox.load(message.key()).orElse(null);
    if (record == null) {
      LOG.log(Level.WARNING, "event=inbox_item_missing lane={0}", message.lane());
      return ProcessingOutcome.UNPROCESSABLE;
    }

    CreationResult creation =
        repository.createPendingIfAbsent(
            new ProposalSeed(message.key(), message.tenant(), message.lane()));
    Proposal proposal = creation.proposal();
    if (proposal.isFinal()) {
      return ProcessingOutcome.COMPLETED;
    }

    Instant sentAt = clock.now();
    Proposal sent = proposal.markSentToLegacy(sentAt);
    repository.save(
        sent,
        List.of(
            ProposalEvent.of(
                message.key(),
                ProposalEvent.SENT_TO_LEGACY,
                sentAt,
                "attempt=" + sent.attempts())));

    LegacyResult result = legacy.submit(toSubmission(record));
    return switch (result) {
      case LegacyResult.Confirmed confirmed -> {
        Instant at = clock.now();
        repository.save(
            sent.confirm(confirmed.averbacaoId(), at),
            List.of(ProposalEvent.of(message.key(), ProposalEvent.CONFIRMED, at, "averbado")));
        business.settled(settlement(message, record, sent, at, true));
        yield ProcessingOutcome.COMPLETED;
      }
      case LegacyResult.Rejected rejected -> {
        Instant at = clock.now();
        repository.save(
            sent.fail(rejected.reason(), at),
            List.of(ProposalEvent.of(message.key(), ProposalEvent.FAILED, at, "rejeitada")));
        business.settled(settlement(message, record, sent, at, false));
        yield ProcessingOutcome.COMPLETED;
      }
      case LegacyResult.TransientFailure transientFailure -> ProcessingOutcome.RETRY;
    };
  }

  private static ProposalSettlement settlement(
      ProposalMessage message, InboxRecord record, Proposal sent, Instant at, boolean confirmed) {
    InboxPayload payload = record.payload();
    Duration timeToSettle =
        record.receivedAtEpochMillis() == null
            ? null
            : Duration.between(Instant.ofEpochMilli(record.receivedAtEpochMillis()), at);
    return new ProposalSettlement(
        message.tenant(),
        message.lane(),
        payload.convenio(),
        payload.portability(),
        payload.amount(),
        sent.attempts(),
        confirmed,
        timeToSettle);
  }

  private static LegacySubmission toSubmission(InboxRecord record) {
    InboxPayload payload = record.payload();
    return new LegacySubmission(
        record.key(), payload.cpf(), payload.convenio(), payload.amount(), payload.installments());
  }
}
