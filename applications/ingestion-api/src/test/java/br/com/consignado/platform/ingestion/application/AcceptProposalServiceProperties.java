package br.com.consignado.platform.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.ingestion.application.Fakes.FakeInbox;
import br.com.consignado.platform.ingestion.application.Fakes.FakeProtection;
import br.com.consignado.platform.ingestion.application.Fakes.FakeQuota;
import br.com.consignado.platform.ingestion.application.Fakes.FixedClock;
import br.com.consignado.platform.ingestion.application.Fakes.SequenceTracing;
import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalCommand;
import br.com.consignado.platform.ingestion.application.usecase.AcceptProposalService;
import br.com.consignado.platform.ingestion.domain.IngestionOutcome;
import br.com.consignado.platform.shared.testkit.DomainArbitraries;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Idempotencia do aceite (PBT-04): para qualquer sequencia de comandos com chaves repetidas, ha
 * exatamente um RECEIVED por chave distinta e o restante e DUPLICATE_ACCEPTED, sem reescrever.
 */
class AcceptProposalServiceProperties {

  @Provide
  Arbitrary<List<AcceptProposalCommand>> sequences() {
    return command().list().ofMinSize(1).ofMaxSize(60);
  }

  private static Arbitrary<AcceptProposalCommand> command() {
    Arbitrary<IdempotencyKey> keyPool =
        Arbitraries.integers()
            .between(0, 9)
            .map(i -> new IdempotencyKey(new UUID(0L, i.longValue())));
    return Combinators.combine(
            keyPool,
            DomainArbitraries.tenantId(),
            DomainArbitraries.validCpf(),
            DomainArbitraries.convenio(),
            Arbitraries.of(true, false),
            DomainArbitraries.money(),
            Arbitraries.integers().between(1, 120))
        .as(AcceptProposalCommand::new);
  }

  @Property
  void exactly_one_received_per_distinct_key(
      @ForAll("sequences") List<AcceptProposalCommand> sequence) {
    FakeInbox inbox = new FakeInbox();
    AcceptProposalService service =
        new AcceptProposalService(
            new FakeQuota(),
            new FakeProtection(),
            inbox,
            new FixedClock(),
            new SequenceTracing(),
            Duration.ofDays(7));
    Set<IdempotencyKey> seen = new HashSet<>();

    for (AcceptProposalCommand command : sequence) {
      boolean first = seen.add(command.idempotencyKey());
      IngestionOutcome outcome = service.accept(command).outcome();

      assertThat(outcome)
          .isEqualTo(first ? IngestionOutcome.RECEIVED : IngestionOutcome.DUPLICATE_ACCEPTED);
    }

    assertThat(inbox.items).hasSize(seen.size());
  }

  @Property
  void the_stored_item_is_the_first_one_written_for_a_key(
      @ForAll("sequences") List<AcceptProposalCommand> sequence) {
    FakeInbox inbox = new FakeInbox();
    AcceptProposalService service =
        new AcceptProposalService(
            new FakeQuota(),
            new FakeProtection(),
            inbox,
            new FixedClock(),
            new SequenceTracing(),
            Duration.ofDays(7));

    for (AcceptProposalCommand command : sequence) {
      service.accept(command);
    }

    for (AcceptProposalCommand command : sequence) {
      AcceptProposalCommand firstForKey =
          sequence.stream()
              .filter(c -> c.idempotencyKey().equals(command.idempotencyKey()))
              .findFirst()
              .orElseThrow();
      assertThat(inbox.items.get(command.idempotencyKey()).tenant())
          .isEqualTo(firstForKey.tenant());
      assertThat(inbox.items.get(command.idempotencyKey()).amount())
          .isEqualTo(firstForKey.amount());
    }
  }
}
