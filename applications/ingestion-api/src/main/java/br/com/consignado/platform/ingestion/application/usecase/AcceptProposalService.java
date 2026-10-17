package br.com.consignado.platform.ingestion.application.usecase;

import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalCommand;
import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalResult;
import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalUseCase;
import br.com.consignado.platform.ingestion.application.port.out.ClockPort;
import br.com.consignado.platform.ingestion.application.port.out.DurableInboxPort;
import br.com.consignado.platform.ingestion.application.port.out.InboxItem;
import br.com.consignado.platform.ingestion.application.port.out.InboxWriteResult;
import br.com.consignado.platform.ingestion.application.port.out.PayloadProtectionPort;
import br.com.consignado.platform.ingestion.application.port.out.TenantQuotaPort;
import br.com.consignado.platform.ingestion.application.port.out.TracingIdPort;
import br.com.consignado.platform.ingestion.domain.IngestionOutcome;
import br.com.consignado.platform.ingestion.domain.TenantQuotaExceededException;
import br.com.consignado.platform.shared.payload.InboxPayload;
import java.time.Duration;
import java.time.Instant;

/**
 * Aceita a proposta com durabilidade e idempotencia: cota do parceiro, protecao do payload e
 * escrita condicional atomica. Nao chama legado nem banco relacional na borda.
 */
public class AcceptProposalService implements AcceptProposalUseCase {

  private final TenantQuotaPort quota;
  private final PayloadProtectionPort protection;
  private final DurableInboxPort inbox;
  private final ClockPort clock;
  private final TracingIdPort tracing;
  private final Duration retention;
  private final br.com.consignado.platform.ingestion.application.port.out.BusinessFingerprintPort
      fingerprints;

  public AcceptProposalService(
      TenantQuotaPort quota,
      PayloadProtectionPort protection,
      DurableInboxPort inbox,
      ClockPort clock,
      TracingIdPort tracing,
      Duration retention) {
    this(quota, protection, inbox, clock, tracing, retention, null);
  }

  public AcceptProposalService(
      TenantQuotaPort quota,
      PayloadProtectionPort protection,
      DurableInboxPort inbox,
      ClockPort clock,
      TracingIdPort tracing,
      Duration retention,
      br.com.consignado.platform.ingestion.application.port.out.BusinessFingerprintPort
          fingerprints) {
    this.quota = quota;
    this.protection = protection;
    this.inbox = inbox;
    this.clock = clock;
    this.tracing = tracing;
    this.retention = retention;
    this.fingerprints = fingerprints;
  }

  @Override
  public AcceptProposalResult accept(AcceptProposalCommand command) {
    if (!quota.tryAcquire(command.tenant())) {
      throw new TenantQuotaExceededException(command.tenant());
    }
    Instant now = clock.now();
    String tracingId = tracing.currentTracingId();
    String protectedPayload =
        protection.protect(
            InboxPayload.of(
                command.cpf(),
                command.convenio(),
                command.portability(),
                command.amount(),
                command.installments()));

    InboxWriteResult written =
        inbox.putIfAbsent(
            new InboxItem(
                command.idempotencyKey(),
                command.tenant(),
                command.convenio(),
                command.portability(),
                command.amount(),
                protectedPayload,
                now.toEpochMilli(),
                now.plus(retention).getEpochSecond(),
                tracingId,
                fingerprints == null
                    ? null
                    : fingerprints.generate(
                        command, now.atOffset(java.time.ZoneOffset.UTC).toLocalDate())));

    if (written.created()) {
      return new AcceptProposalResult(
          IngestionOutcome.RECEIVED,
          command.idempotencyKey(),
          tracingId,
          now,
          written.proposalId(),
          false);
    }
    String originalTracingId =
        written.existingTracingId() != null ? written.existingTracingId() : tracingId;
    Instant originalReceivedAt =
        written.existingCreatedAt() != null
            ? Instant.ofEpochMilli(written.existingCreatedAt())
            : now;
    return new AcceptProposalResult(
        IngestionOutcome.DUPLICATE_ACCEPTED,
        command.idempotencyKey(),
        originalTracingId,
        originalReceivedAt,
        written.proposalId(),
        written.semanticDuplicate());
  }
}
