package br.com.consignado.platform.localenv.incident;

/**
 * Evento de incidente de um episodio de alerta ({@code fingerprint@startsAt}). Somente labels
 * categoricos e anotacoes operacionais do alerta: nenhum payload ou identificador de negocio.
 *
 * @param status {@code opened} ou {@code resolved}
 */
public record Incident(
    String episode,
    String status,
    String alertname,
    String severity,
    String slo,
    String lane,
    String component,
    String summary,
    String startsAt,
    String endsAt,
    String runbook,
    String dashboardPath,
    String jaegerService,
    String jaegerOperation,
    String recordedAt) {

  public static final String OPENED = "opened";
  public static final String RESOLVED = "resolved";
}
