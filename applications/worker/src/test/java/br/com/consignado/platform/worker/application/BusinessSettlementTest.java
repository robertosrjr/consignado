package br.com.consignado.platform.worker.application;

import static br.com.consignado.platform.worker.application.WorkerFixtures.KEY;
import static br.com.consignado.platform.worker.application.WorkerFixtures.message;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.payload.InboxPayload;
import br.com.consignado.platform.shared.vo.Cpf;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.worker.application.WorkerFixtures.FixedClock;
import br.com.consignado.platform.worker.application.WorkerFixtures.ScriptedLegacy;
import br.com.consignado.platform.worker.application.port.out.InboxRecord;
import br.com.consignado.platform.worker.application.port.out.ProposalSettlement;
import br.com.consignado.platform.worker.application.usecase.ProcessProposalService;
import br.com.consignado.platform.worker.domain.LegacyResult;
import br.com.consignado.platform.worker.infrastructure.adapter.out.inbox.InMemoryInboxReadAdapter;
import br.com.consignado.platform.worker.infrastructure.adapter.out.persistence.InMemoryProposalRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BusinessSettlementTest {

  private final FixedClock clock = new FixedClock();
  private final InMemoryInboxReadAdapter inbox = new InMemoryInboxReadAdapter();
  private final ScriptedLegacy legacy = new ScriptedLegacy();
  private final List<ProposalSettlement> settlements = new ArrayList<>();
  private final ProcessProposalService service =
      new ProcessProposalService(
          new InMemoryProposalRepository(clock), inbox, legacy, clock, settlements::add);

  private void putInbox(boolean portability, long receivedAtMillis) {
    inbox.put(
        new InboxRecord(
            KEY,
            WorkerFixtures.TENANT,
            InboxPayload.of(
                new Cpf("52998224725"),
                ConvenioType.SIAPE_PUBLICO,
                portability,
                Money.of("2500.50"),
                24),
            "trace-1",
            receivedAtMillis));
  }

  @Test
  void should_emit_one_confirmed_settlement_with_value_and_time_to_settle() {
    Instant now = clock.now();
    putInbox(true, now.minusSeconds(90).toEpochMilli());
    legacy.script.add(new LegacyResult.Confirmed("av-1"));

    service.process(message());

    assertThat(settlements).hasSize(1);
    ProposalSettlement s = settlements.get(0);
    assertThat(s.confirmed()).isTrue();
    assertThat(s.convenio()).isEqualTo(ConvenioType.SIAPE_PUBLICO);
    assertThat(s.portability()).isTrue();
    assertThat(s.amount()).isEqualTo(Money.of("2500.50"));
    assertThat(s.attempts()).isEqualTo(1);
    assertThat(s.timeToSettle()).isEqualTo(Duration.ofSeconds(90));
  }

  @Test
  void should_emit_a_rejected_settlement_and_not_repeat_it_on_a_redelivery() {
    putInbox(false, clock.now().toEpochMilli());
    legacy.script.add(new LegacyResult.Rejected("margem"));

    service.process(message());
    service.process(message());

    assertThat(settlements).hasSize(1);
    assertThat(settlements.get(0).confirmed()).isFalse();
  }

  @Test
  void should_count_the_retry_as_a_second_attempt_and_leave_time_unknown_without_receipt() {
    inbox.put(
        new InboxRecord(
            KEY,
            WorkerFixtures.TENANT,
            InboxPayload.of(
                new Cpf("52998224725"), ConvenioType.INSS, false, Money.of("1000.00"), 12),
            "trace-2"));
    legacy.script.add(new LegacyResult.TransientFailure());
    legacy.script.add(new LegacyResult.Confirmed("av-2"));

    service.process(message());
    service.process(message());

    assertThat(settlements).hasSize(1);
    assertThat(settlements.get(0).attempts()).isEqualTo(2);
    assertThat(settlements.get(0).timeToSettle()).isNull();
  }
}
