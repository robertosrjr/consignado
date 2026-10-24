package br.com.consignado.platform.localenv.incident;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converte a notificacao do Alertmanager (envelope SNS cujo {@code Message} segue o template {@code
 * consignado.sns.message}) em eventos de incidente deduplicados por episodio: um {@code opened} por
 * {@code fingerprint@startsAt} e um {@code resolved} ao fim. Repeticoes de firing (repeat_interval,
 * reentrega da fila) nao abrem outro incidente.
 */
public final class IncidentProcessor {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final IncidentNotifier notifier;
  private final Clock clock;
  private final Map<String, String> lastStatus = new HashMap<>();

  public IncidentProcessor(IncidentNotifier notifier, Clock clock, List<Incident> history) {
    this.notifier = notifier;
    this.clock = clock;
    for (Incident past : history) {
      lastStatus.put(past.episode(), past.status());
    }
  }

  /**
   * Processa um corpo de mensagem da fila. Publica antes de memorizar: se o destino falhar, a
   * excecao propaga, a mensagem nao e apagada e a reentrega tenta de novo.
   *
   * @return eventos publicados nesta chamada
   * @throws IllegalArgumentException se o corpo nao for uma notificacao valida (mensagem veneno)
   */
  public synchronized List<Incident> process(String body) {
    JsonNode notification = parse(parse(body).path("Message").asString(""));
    JsonNode alerts = notification.path("alerts");
    if (!alerts.isArray()) {
      throw new IllegalArgumentException("Notificacao sem lista de alertas");
    }
    List<Incident> published = new ArrayList<>();
    for (JsonNode alert : alerts) {
      String fingerprint = alert.path("fingerprint").asString("");
      String startsAt = alert.path("startsAt").asString("");
      if (fingerprint.isBlank() || startsAt.isBlank()) {
        throw new IllegalArgumentException("Alerta sem fingerprint ou startsAt");
      }
      String episode = fingerprint + "@" + startsAt;
      String status =
          "resolved".equals(alert.path("status").asString(""))
              ? Incident.RESOLVED
              : Incident.OPENED;
      String previous = lastStatus.get(episode);
      if (status.equals(previous) || Incident.RESOLVED.equals(previous)) {
        continue; // repeticao do mesmo estado, ou firing atrasado de episodio ja encerrado
      }
      Incident incident = toIncident(episode, status, alert);
      notifier.publish(incident);
      lastStatus.put(episode, status);
      published.add(incident);
    }
    return published;
  }

  private Incident toIncident(String episode, String status, JsonNode alert) {
    JsonNode labels = alert.path("labels");
    JsonNode annotations = alert.path("annotations");
    return new Incident(
        episode,
        status,
        text(labels, "alertname"),
        text(labels, "severity"),
        text(labels, "slo"),
        text(labels, "lane"),
        text(labels, "component"),
        text(annotations, "summary"),
        text(alert, "startsAt"),
        Incident.RESOLVED.equals(status) ? text(alert, "endsAt") : null,
        text(annotations, "runbook"),
        text(annotations, "dashboard_path"),
        text(annotations, "jaeger_service"),
        text(annotations, "jaeger_operation"),
        clock.instant().toString());
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asString();
  }

  private static JsonNode parse(String json) {
    try {
      return JSON.readTree(json);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Notificacao de alerta invalida", e);
    }
  }
}
