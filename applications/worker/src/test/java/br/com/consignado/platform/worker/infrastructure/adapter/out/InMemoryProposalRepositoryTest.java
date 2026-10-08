package br.com.consignado.platform.worker.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;
import br.com.consignado.platform.worker.application.port.out.CreationResult;
import br.com.consignado.platform.worker.application.port.out.ProposalSeed;
import br.com.consignado.platform.worker.domain.Proposal;
import br.com.consignado.platform.worker.domain.ProposalEvent;
import br.com.consignado.platform.worker.domain.ProposalStatus;
import br.com.consignado.platform.worker.infrastructure.adapter.out.persistence.InMemoryProposalRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InMemoryProposalRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

  private final InMemoryProposalRepository repository = new InMemoryProposalRepository(() -> NOW);

  private static ProposalSeed seed(IdempotencyKey key) {
    return new ProposalSeed(key, new TenantId("banco-a"), Lane.FAST);
  }

  private static IdempotencyKey newKey() {
    return new IdempotencyKey(UUID.randomUUID());
  }

  @Test
  void should_create_pending_once_and_record_the_creation_event() {
    IdempotencyKey key = newKey();

    CreationResult first = repository.createPendingIfAbsent(seed(key));
    CreationResult second = repository.createPendingIfAbsent(seed(key));

    assertThat(first.created()).isTrue();
    assertThat(second.created()).isFalse();
    assertThat(second.proposal()).isEqualTo(first.proposal());
    assertThat(repository.history(key))
        .extracting(ProposalEvent::type)
        .containsExactly("PROPOSAL_CREATED");
    assertThat(repository.count(ProposalStatus.PENDING)).isEqualTo(1);
    assertThat(repository.size()).isEqualTo(1);
  }

  @Test
  void should_save_state_and_events_atomically_and_update_counters() {
    IdempotencyKey key = newKey();
    Proposal pending = repository.createPendingIfAbsent(seed(key)).proposal();
    Proposal sent = pending.markSentToLegacy(NOW);

    repository.save(sent, List.of(ProposalEvent.of(key, "SENT_TO_LEGACY", NOW, "attempt=1")));

    assertThat(repository.find(key).orElseThrow().status())
        .isEqualTo(ProposalStatus.SENT_TO_LEGACY);
    assertThat(repository.history(key)).hasSize(2);
    assertThat(repository.count(ProposalStatus.PENDING)).isZero();
    assertThat(repository.count(ProposalStatus.SENT_TO_LEGACY)).isEqualTo(1);
  }

  @Test
  void should_never_regress_a_final_state_and_should_drop_the_stale_events() {
    IdempotencyKey key = newKey();
    Proposal pending = repository.createPendingIfAbsent(seed(key)).proposal();
    Proposal sent = pending.markSentToLegacy(NOW);
    repository.save(sent, List.of());
    repository.save(
        sent.confirm("av-1", NOW), List.of(ProposalEvent.of(key, "CONFIRMED", NOW, "")));

    repository.save(sent, List.of(ProposalEvent.of(key, "SENT_TO_LEGACY", NOW, "atrasado")));
    repository.save(pending, List.of(ProposalEvent.of(key, "X", NOW, "")));

    assertThat(repository.find(key).orElseThrow().status()).isEqualTo(ProposalStatus.CONFIRMED);
    assertThat(repository.history(key)).extracting(ProposalEvent::type).doesNotContain("X");
    assertThat(repository.count(ProposalStatus.CONFIRMED)).isEqualTo(1);
    assertThat(repository.count(ProposalStatus.SENT_TO_LEGACY)).isZero();
  }

  @Test
  void should_ignore_a_save_that_would_move_to_an_earlier_state() {
    IdempotencyKey key = newKey();
    Proposal pending = repository.createPendingIfAbsent(seed(key)).proposal();
    repository.save(pending.markSentToLegacy(NOW), List.of());

    repository.save(pending, List.of(ProposalEvent.of(key, "REGRESSAO", NOW, "")));

    assertThat(repository.find(key).orElseThrow().status())
        .isEqualTo(ProposalStatus.SENT_TO_LEGACY);
    assertThat(repository.history(key)).extracting(ProposalEvent::type).doesNotContain("REGRESSAO");
  }

  @Test
  void should_return_an_empty_history_and_no_proposal_for_unknown_keys() {
    assertThat(repository.find(newKey())).isEmpty();
    assertThat(repository.history(newKey())).isEmpty();
  }

  @Test
  void should_create_exactly_one_proposal_when_many_threads_race_on_the_same_key()
      throws Exception {
    IdempotencyKey key = newKey();
    int threads = 64;
    AtomicInteger created = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int i = 0; i < threads; i++) {
        executor.submit(
            () -> {
              start.await();
              if (repository.createPendingIfAbsent(seed(key)).created()) {
                created.incrementAndGet();
              }
              return null;
            });
      }
      start.countDown();
    }

    assertThat(created.get()).isEqualTo(1);
    assertThat(repository.size()).isEqualTo(1);
    assertThat(repository.history(key)).hasSize(1);
    assertThat(repository.count(ProposalStatus.PENDING)).isEqualTo(1);
  }
}
