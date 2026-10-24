package br.com.consignado.platform.dispatcher.dispatch;

import static br.com.consignado.platform.dispatcher.dispatch.DispatchFixtures.insert;
import static br.com.consignado.platform.dispatcher.dispatch.DispatchFixtures.service;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrphanSweeperTest {
  @Test
  void should_retry_after_a_publication_failure_without_losing_the_orphan() {
    var publisher = new DispatchFixtures.FakePublisher();
    var updater = new DispatchFixtures.FakeUpdater();
    var dispatch = service(publisher, updater);
    var record = insert("1");
    var sweeper =
        new OrphanSweeper(
            (cutoff, limit) -> {
              assertThat(cutoff).isEqualTo(60_000);
              return updater.marked.isEmpty() ? List.of(record) : List.of();
            },
            dispatch,
            Clock.fixed(Instant.ofEpochMilli(90_000), ZoneOffset.UTC),
            Duration.ofSeconds(30),
            100);
    publisher.failure = new IllegalStateException("SQS indisponivel");
    assertThat(sweeper.sweep()).isEqualTo(new OrphanSweeper.SweepResult(1, 0, 1));
    assertThat(updater.marked).isEmpty();
    publisher.failure = null;
    assertThat(sweeper.sweep()).isEqualTo(new OrphanSweeper.SweepResult(1, 1, 0));
    assertThat(sweeper.sweep()).isEqualTo(new OrphanSweeper.SweepResult(0, 0, 0));
    assertThat(publisher.published).hasSize(1);
  }
}
