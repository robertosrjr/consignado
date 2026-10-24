package br.com.consignado.platform.localenv.incident;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/** Registro local de incidentes em JSONL (uma linha por evento), base do historico e da dedup. */
public final class FileIncidentNotifier implements IncidentNotifier {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Path file;

  public FileIncidentNotifier(Path file) {
    this.file = file;
  }

  @Override
  public synchronized void publish(Incident incident) {
    try {
      Files.createDirectories(file.toAbsolutePath().getParent());
      Files.writeString(
          file,
          JSON.writeValueAsString(incident) + System.lineSeparator(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException("Falha ao gravar o registro de incidentes", e);
    }
  }

  /** Eventos ja gravados, para reconstruir a dedup apos reinicio; vazio se nao houver arquivo. */
  public synchronized List<Incident> history() {
    if (!Files.exists(file)) {
      return List.of();
    }
    try {
      List<Incident> events = new ArrayList<>();
      for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
        if (!line.isBlank()) {
          events.add(JSON.readValue(line, Incident.class));
        }
      }
      return events;
    } catch (IOException e) {
      throw new UncheckedIOException("Falha ao ler o registro de incidentes", e);
    }
  }
}
