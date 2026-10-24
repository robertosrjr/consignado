package br.com.consignado.platform.worker.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import br.com.consignado.platform.localenv.LocalDynamoDb;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;
import br.com.consignado.platform.worker.application.port.out.ProposalSeed;
import br.com.consignado.platform.worker.domain.Proposal;
import br.com.consignado.platform.worker.domain.ProposalEvent;
import br.com.consignado.platform.worker.infrastructure.adapter.out.persistence.DynamoDbProposalRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

@SuppressWarnings("try") // O emulador declara close() throws Exception.
class DynamoDbProposalRepositoryIT {
  @Test
  void should_recover_when_the_database_commits_but_the_client_times_out() throws Exception {
    try (LocalDynamoDb server = LocalDynamoDb.start(0);
        var client = server.client()) {
      String table = "WorkerTimeout";
      server.createWorkerProposalTable(client, table);
      DynamoDbClient responseLost = mock(DynamoDbClient.class);
      doAnswer(
              invocation -> {
                client.transactWriteItems((TransactWriteItemsRequest) invocation.getArgument(0));
                throw SdkClientException.create("timeout apos commit");
              })
          .when(responseLost)
          .transactWriteItems(any(TransactWriteItemsRequest.class));
      Instant at = Instant.parse("2026-10-23T20:00:00Z");
      var seed =
          new ProposalSeed(
              IdempotencyKey.parse(UUID.randomUUID().toString()),
              new TenantId("banco-a"),
              Lane.FAST);
      var uncertain = new DynamoDbProposalRepository(responseLost, table, () -> at);
      assertThatThrownBy(() -> uncertain.createPendingIfAbsent(seed))
          .isInstanceOf(IllegalStateException.class);
      var recovered = new DynamoDbProposalRepository(client, table, () -> at.plusSeconds(1));
      assertThat(recovered.createPendingIfAbsent(seed).created()).isFalse();
      assertThat(recovered.history(seed.key()))
          .extracting(ProposalEvent::type)
          .containsExactly(ProposalEvent.CREATED);
    }
  }

  @Test
  void should_preserve_atomic_state_and_history_across_worker_restarts_and_replays()
      throws Exception {
    try (LocalDynamoDb server = LocalDynamoDb.start(0);
        var client = server.client()) {
      String table = "WorkerRecovery";
      server.createWorkerProposalTable(client, table);
      Instant at = Instant.parse("2026-10-23T20:00:00Z");
      var first = new DynamoDbProposalRepository(client, table, () -> at);
      var key = IdempotencyKey.parse(UUID.randomUUID().toString());
      var seed = new ProposalSeed(key, new TenantId("banco-a"), Lane.FAST);
      Proposal pending = first.createPendingIfAbsent(seed).proposal();
      Proposal sent = pending.markSentToLegacy(at.plusSeconds(1));
      first.save(
          sent,
          List.of(ProposalEvent.of(key, ProposalEvent.SENT_TO_LEGACY, sent.updatedAt(), "sent")));

      var restarted = new DynamoDbProposalRepository(client, table, () -> at.plusSeconds(2));
      assertThat(restarted.createPendingIfAbsent(seed).created()).isFalse();
      assertThat(restarted.find(key)).contains(sent);
      Proposal confirmed = sent.confirm("av-recovery", at.plusSeconds(3));
      restarted.save(
          confirmed,
          List.of(ProposalEvent.of(key, ProposalEvent.CONFIRMED, confirmed.updatedAt(), "ok")));
      // Resposta de commit perdida: repetir a escrita e tentar regredir nao altera o terminal.
      restarted.save(
          confirmed,
          List.of(ProposalEvent.of(key, ProposalEvent.CONFIRMED, confirmed.updatedAt(), "ok")));
      first.save(sent, List.of());

      var afterAckLoss = new DynamoDbProposalRepository(client, table, () -> at.plusSeconds(4));
      assertThat(afterAckLoss.find(key)).contains(confirmed);
      assertThat(afterAckLoss.history(key))
          .extracting(ProposalEvent::type)
          .containsExactly(
              ProposalEvent.CREATED, ProposalEvent.SENT_TO_LEGACY, ProposalEvent.CONFIRMED);
    }
  }
}
