package br.com.consignado.platform.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.ingestion.application.Fakes.FakeInbox;
import br.com.consignado.platform.ingestion.application.Fakes.FakeProtection;
import br.com.consignado.platform.ingestion.application.Fakes.FakeQuota;
import br.com.consignado.platform.ingestion.application.Fakes.FixedClock;
import br.com.consignado.platform.ingestion.application.Fakes.SequenceTracing;
import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalCommand;
import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalResult;
import br.com.consignado.platform.ingestion.application.port.out.InboxItem;
import br.com.consignado.platform.ingestion.application.usecase.AcceptProposalService;
import br.com.consignado.platform.ingestion.domain.InboxUnavailableException;
import br.com.consignado.platform.ingestion.domain.IngestionOutcome;
import br.com.consignado.platform.ingestion.domain.TenantQuotaExceededException;
import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.vo.Cpf;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.shared.vo.TenantId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AcceptProposalServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

  private final FakeInbox inbox = new FakeInbox();
  private final FakeQuota quota = new FakeQuota();
  private final FakeProtection protection = new FakeProtection();
  private final FixedClock clock = new FixedClock();
  private final AcceptProposalService service =
      new AcceptProposalService(
          quota, protection, inbox, clock, new SequenceTracing(), Duration.ofDays(7));

  private static AcceptProposalCommand command(String key) {
    return new AcceptProposalCommand(
        IdempotencyKey.parse(key),
        new TenantId("banco-a"),
        new Cpf("52998224725"),
        ConvenioType.INSS,
        true,
        Money.of("15000.50"),
        84);
  }

  @Test
  void should_receive_a_new_proposal_and_store_it_with_a_seven_day_ttl() {
    AcceptProposalResult result = service.accept(command("e3b0c442-98fc-11ee-b9d1-0242ac120002"));

    assertThat(result.outcome()).isEqualTo(IngestionOutcome.RECEIVED);
    assertThat(result.tracingId()).isEqualTo("trace-1");
    assertThat(result.receivedAt()).isEqualTo(NOW);
    InboxItem stored = inbox.items.values().iterator().next();
    assertThat(stored.ttlEpochSeconds()).isEqualTo(NOW.plus(Duration.ofDays(7)).getEpochSecond());
    assertThat(stored.createdAtEpochMillis()).isEqualTo(NOW.toEpochMilli());
    assertThat(stored.protectedPayload()).isEqualTo("protected-1");
    assertThat(stored.portability()).isTrue();
  }

  @Test
  void should_never_place_the_cpf_in_the_plain_routing_attributes_or_in_to_string() {
    service.accept(command("e3b0c442-98fc-11ee-b9d1-0242ac120002"));
    InboxItem stored = inbox.items.values().iterator().next();

    assertThat(stored.toString()).doesNotContain("52998224725").doesNotContain("protected-1");
  }

  @Test
  void should_answer_duplicate_accepted_with_the_original_tracing_id_and_time() {
    String key = "e3b0c442-98fc-11ee-b9d1-0242ac120002";
    AcceptProposalResult first = service.accept(command(key));
    clock.now = NOW.plusSeconds(30);

    AcceptProposalResult second = service.accept(command(key));

    assertThat(second.outcome()).isEqualTo(IngestionOutcome.DUPLICATE_ACCEPTED);
    assertThat(second.tracingId()).isEqualTo(first.tracingId());
    assertThat(second.receivedAt()).isEqualTo(first.receivedAt());
    assertThat(inbox.items).hasSize(1);
  }

  @Test
  void should_reject_when_the_tenant_quota_is_exhausted_without_touching_the_inbox() {
    quota.allow = false;

    assertThatThrownBy(() -> service.accept(command("e3b0c442-98fc-11ee-b9d1-0242ac120002")))
        .isInstanceOf(TenantQuotaExceededException.class);

    assertThat(inbox.writeAttempts).isEmpty();
    assertThat(protection.calls.get()).isZero();
  }

  @Test
  void should_propagate_an_unavailable_inbox_so_the_proposal_is_not_accepted() {
    inbox.failure = new InboxUnavailableException("indisponivel", null);

    assertThatThrownBy(() -> service.accept(command("e3b0c442-98fc-11ee-b9d1-0242ac120002")))
        .isInstanceOf(InboxUnavailableException.class);

    assertThat(inbox.items).isEmpty();
  }

  @Test
  void should_use_the_new_tracing_id_when_the_store_does_not_return_the_original() {
    inbox.items.put(
        IdempotencyKey.parse("e3b0c442-98fc-11ee-b9d1-0242ac120002"),
        new InboxItem(
            IdempotencyKey.parse("e3b0c442-98fc-11ee-b9d1-0242ac120002"),
            new TenantId("banco-a"),
            ConvenioType.INSS,
            false,
            Money.of("1"),
            "p",
            0L,
            0L,
            null));

    AcceptProposalResult result = service.accept(command("e3b0c442-98fc-11ee-b9d1-0242ac120002"));

    assertThat(result.outcome()).isEqualTo(IngestionOutcome.DUPLICATE_ACCEPTED);
    assertThat(result.tracingId()).isEqualTo("trace-1");
  }
}
