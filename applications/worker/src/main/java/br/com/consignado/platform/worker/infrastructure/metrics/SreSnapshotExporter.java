package br.com.consignado.platform.worker.infrastructure.metrics;

import br.com.consignado.platform.worker.infrastructure.adapter.out.persistence.InMemoryProposalRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.json.JsonMapper;

/** Opt-in measurement for a single uninterrupted local JVM, not durable business storage. */
public final class SreSnapshotExporter implements AutoCloseable {
  private final InMemoryProposalRepository repository;
  private final Path destination;
  private final String instanceId = UUID.randomUUID().toString();
  private final String startedAt = Instant.now().toString();
  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().daemon().name("sre-local-snapshot").factory());
  private final JsonMapper json = JsonMapper.builder().build();
  private boolean failed;

  public record Snapshot(
      int schemaVersion,
      String instanceId,
      String startedAt,
      String observedAt,
      java.util.List<InMemoryProposalRepository.CaptureState> states) {}

  public SreSnapshotExporter(InMemoryProposalRepository repository, Path destination)
      throws Exception {
    this.repository = repository;
    this.destination = destination.toAbsolutePath();
    Files.createDirectories(this.destination.getParent());
    // Fail startup if explicitly requested capture cannot produce its initial snapshot.
    writeSnapshot();
    executor.scheduleWithFixedDelay(this::refresh, 1, 1, TimeUnit.SECONDS);
  }

  private void refresh() {
    try {
      writeSnapshot();
      failed = false;
    } catch (Exception error) {
      if (!failed)
        System.getLogger(SreSnapshotExporter.class.getName())
            .log(System.Logger.Level.WARNING, "event=sre_capture_unavailable");
      failed = true;
      // Old file is deliberately not refreshed; collector rejects stale observation cuts.
    }
  }

  public synchronized void writeSnapshot() throws Exception {
    var cut = repository.captureSnapshot();
    Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
    Files.writeString(
        temporary,
        json.writeValueAsString(
            new Snapshot(1, instanceId, startedAt, cut.observedAt(), cut.states())));
    Files.move(
        temporary,
        destination,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
  }

  @Override
  public void close() {
    executor.shutdownNow();
    // A final write must not masquerade as a live exporter after shutdown.
  }
}
