package br.com.consignado.platform.worker.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;
import br.com.consignado.platform.worker.application.port.out.ProposalSeed;
import br.com.consignado.platform.worker.infrastructure.adapter.out.persistence.InMemoryProposalRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class SreSnapshotExporterTest {
  @TempDir Path directory;

  @Test
  void snapshotUsesTerminalCommitTimeAndExcludesPayloadIdentityOfCustomerAndFinancialValues()
      throws Exception {
    Instant initial = Instant.parse("2026-10-17T12:00:00Z");
    var clock = new AtomicReference<>(initial);
    var repository = new InMemoryProposalRepository(clock::get);
    var key = new IdempotencyKey(UUID.randomUUID());
    var pending =
        repository
            .createPendingIfAbsent(new ProposalSeed(key, new TenantId("private-tenant"), Lane.FAST))
            .proposal();
    var sent = pending.markSentToLegacy(initial.plusSeconds(1));
    clock.set(initial.plusSeconds(3));
    repository.save(sent.confirm("private-legacy", initial.plusSeconds(2)), List.of());
    var snapshot = repository.captureSnapshot();
    assertThat(snapshot.states().getFirst().committedAt())
        .isEqualTo(initial.plusSeconds(3).toString());
    clock.set(initial.plusSeconds(4));
    repository.save(sent.fail("sensitive failure", initial.plusSeconds(4)), List.of());
    assertThat(repository.captureSnapshot().states().getFirst().status()).isEqualTo("CONFIRMED");
    assertThat(repository.captureSnapshot().states().getFirst().committedAt())
        .isEqualTo(initial.plusSeconds(3).toString());
    Path file = directory.resolve("snapshot.json");
    try (var exporter = new SreSnapshotExporter(repository, file)) {
      exporter.writeSnapshot();
      var result =
          JsonMapper.builder()
              .build()
              .readValue(Files.readAllBytes(file), SreSnapshotExporter.Snapshot.class);
      assertThat(result.states()).hasSize(1);
      assertThat(result.instanceId()).isNotBlank();
      assertThat(Files.readString(file))
          .doesNotContain(
              "private-tenant",
              "private-legacy",
              "sensitive failure",
              "amount",
              "payload",
              "trace");
      assertThat(Files.exists(directory.resolve("snapshot.json.tmp"))).isFalse();
    }
  }
}
