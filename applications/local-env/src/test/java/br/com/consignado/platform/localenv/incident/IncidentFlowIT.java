package br.com.consignado.platform.localenv.incident;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.localenv.LocalSqs;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * ElasticMQ real (sem Docker): webhook do Alertmanager -> ponte -> fila sqs-alerts -> consumidor ->
 * JSONL. Prova o caminho ate o destino final e a dedup por episodio na reentrega.
 */
class IncidentFlowIT {

  @TempDir Path dir;

  private static String webhook(String status) {
    return """
        {"receiver":"consignado-local","status":"%1$s","alerts":[{"status":"%1$s",
        "fingerprint":"f1","startsAt":"2026-10-23T19:55:00Z","endsAt":"2026-10-23T20:10:00Z",
        "labels":{"alertname":"ConsignadoDlqNotEmpty","severity":"critical","component":"worker"},
        "annotations":{"summary":"Mensagens na DLQ","runbook":"ALERTAS.md#consignadodlqnotempty"}}]}
        """
        .formatted(status);
  }

  private static int post(HttpClient http, String url, String body) throws Exception {
    return http.send(
            HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.discarding())
        .statusCode();
  }

  @Test
  void alertaChegaAoRegistroUmaVezPorEpisodio() throws Exception {
    Path file = dir.resolve("incidents.jsonl");
    try (LocalSqs sqs = LocalSqs.start(0);
        SqsClient client = sqs.client();
        HttpClient http = HttpClient.newHttpClient()) {
      String queue = sqs.createAlertQueue(client);
      FileIncidentNotifier log = new FileIncidentNotifier(file);
      IncidentQueueConsumer consumer =
          new IncidentQueueConsumer(
              client, queue, new IncidentProcessor(log, Clock.systemUTC(), log.history()));
      try (AlertWebhookBridge bridge = AlertWebhookBridge.start(0, client, queue)) {
        assertThat(post(http, bridge.url(), webhook("firing"))).isEqualTo(202);
        assertThat(post(http, bridge.url(), webhook("firing"))).isEqualTo(202); // repeat
        assertThat(post(http, bridge.url(), webhook("resolved"))).isEqualTo(202);
        assertThat(post(http, bridge.url(), "{invalido")).isEqualTo(400);

        int handled = 0;
        for (int i = 0; i < 5 && handled < 3; i++) {
          handled += consumer.pollOnce(1);
        }
        assertThat(handled).isEqualTo(3);
      }

      List<Incident> events = log.history();
      assertThat(events).extracting(Incident::status).containsExactly("opened", "resolved");
      assertThat(events).allMatch(e -> e.episode().equals("f1@2026-10-23T19:55:00Z"));
      assertThat(Files.readAllLines(file)).hasSize(2);
    }
  }
}
