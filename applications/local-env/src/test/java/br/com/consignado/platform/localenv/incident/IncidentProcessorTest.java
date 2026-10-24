package br.com.consignado.platform.localenv.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class IncidentProcessorTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-23T20:00:00Z"), ZoneOffset.UTC);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final List<Incident> published = new ArrayList<>();
  private final IncidentProcessor processor =
      new IncidentProcessor(published::add, CLOCK, List.of());

  static String envelope(String status, String fingerprint, String startsAt) {
    String message =
        """
        {"receiver":"consignado-local","status":"%1$s","alerts":[{"status":"%1$s",
        "fingerprint":"%2$s","startsAt":"%3$s","endsAt":"2026-10-23T20:10:00Z",
        "labels":{"alertname":"ConsignadoJ2DeadlineBurnFast","severity":"critical",
        "slo":"j2-deadline","lane":"fast","component":"worker","cpf":"52998224725"},
        "annotations":{"summary":"J2: queima rapida","runbook":"ALERTAS.md#consignadoj2deadlineburn",
        "dashboard_path":"/d/consignado-slo-budget/x","jaeger_service":"worker",
        "jaeger_operation":"process proposal"}}]}
        """
            .formatted(status, fingerprint, startsAt);
    return JSON.writeValueAsString(java.util.Map.of("Type", "Notification", "Message", message));
  }

  @Test
  void abreUmIncidentePorEpisodioMesmoComRepeticoes() {
    processor.process(envelope("firing", "abc", "2026-10-23T19:55:00Z"));
    processor.process(envelope("firing", "abc", "2026-10-23T19:55:00Z"));

    assertThat(published).hasSize(1);
    Incident incident = published.getFirst();
    assertThat(incident.episode()).isEqualTo("abc@2026-10-23T19:55:00Z");
    assertThat(incident.status()).isEqualTo(Incident.OPENED);
    assertThat(incident.lane()).isEqualTo("fast");
    assertThat(incident.runbook()).isEqualTo("ALERTAS.md#consignadoj2deadlineburn");
    assertThat(incident.endsAt()).isNull();
  }

  @Test
  void fechaOEpisodioUmaVezEIgnoraFiringAtrasado() {
    processor.process(envelope("firing", "abc", "2026-10-23T19:55:00Z"));
    processor.process(envelope("resolved", "abc", "2026-10-23T19:55:00Z"));
    processor.process(envelope("resolved", "abc", "2026-10-23T19:55:00Z"));
    processor.process(envelope("firing", "abc", "2026-10-23T19:55:00Z"));

    assertThat(published).extracting(Incident::status).containsExactly("opened", "resolved");
    assertThat(published.getLast().endsAt()).isEqualTo("2026-10-23T20:10:00Z");
  }

  @Test
  void novoInicioDoMesmoAlertaENovoEpisodio() {
    processor.process(envelope("firing", "abc", "2026-10-23T19:55:00Z"));
    processor.process(envelope("firing", "abc", "2026-10-23T21:00:00Z"));

    assertThat(published).hasSize(2);
  }

  @Test
  void historicoEvitaReabrirAposReinicio() {
    processor.process(envelope("firing", "abc", "2026-10-23T19:55:00Z"));
    IncidentProcessor restarted =
        new IncidentProcessor(published::add, CLOCK, List.copyOf(published));

    restarted.process(envelope("firing", "abc", "2026-10-23T19:55:00Z"));

    assertThat(published).hasSize(1);
  }

  @Test
  void naoCopiaLabelsForaDaListaPermitida() throws Exception {
    processor.process(envelope("firing", "abc", "2026-10-23T19:55:00Z"));

    assertThat(JSON.writeValueAsString(published.getFirst())).doesNotContain("52998224725");
  }

  @Test
  void falhaDoDestinoNaoMemorizaOEpisodio() {
    List<Incident> ok = new ArrayList<>();
    boolean[] fail = {true};
    IncidentProcessor flaky =
        new IncidentProcessor(
            incident -> {
              if (fail[0]) {
                throw new IllegalStateException("destino fora");
              }
              ok.add(incident);
            },
            CLOCK,
            List.of());

    assertThatThrownBy(() -> flaky.process(envelope("firing", "abc", "2026-10-23T19:55:00Z")))
        .isInstanceOf(IllegalStateException.class);
    fail[0] = false;
    flaky.process(envelope("firing", "abc", "2026-10-23T19:55:00Z"));

    assertThat(ok).hasSize(1);
  }

  @Test
  void mensagemInvalidaEVeneno() {
    assertThatThrownBy(() -> processor.process("nao e json"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                processor.process(
                    JSON.writeValueAsString(java.util.Map.of("Message", "{\"alerts\":[{}]}"))))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
