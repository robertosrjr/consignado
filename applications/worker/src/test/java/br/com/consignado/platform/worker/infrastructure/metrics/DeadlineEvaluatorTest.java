package br.com.consignado.platform.worker.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.worker.application.port.out.DeadlineIndexPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeadlineEvaluatorTest {

  private static final long NOW = Instant.parse("2026-10-23T12:00:00Z").toEpochMilli();
  private static final Duration FAST = Duration.ofMinutes(2);
  private static final Duration STANDARD = Duration.ofMinutes(45);

  /** Indice em memoria com a mesma semantica condicional do adaptador DynamoDB. */
  private static final class FakeIndex implements DeadlineIndexPort {
    record Item(Lane lane, OpenProposal proposal) {}

    final Map<IdempotencyKey, Item> open = new HashMap<>();
    final Map<IdempotencyKey, String> outcomes = new HashMap<>();

    void add(Lane lane, long createdAt, Long decidedAt) {
      IdempotencyKey key = IdempotencyKey.parse(UUID.randomUUID().toString());
      open.put(key, new Item(lane, new OpenProposal(key, createdAt, decidedAt)));
    }

    @Override
    public List<OpenProposal> acceptedUpTo(Lane lane, long createdAtOrBefore, int limit) {
      List<OpenProposal> result = new ArrayList<>();
      for (Item item : open.values()) {
        if (item.lane() == lane && item.proposal().createdAtEpochMillis() <= createdAtOrBefore) {
          result.add(item.proposal());
        }
      }
      return result.subList(0, Math.min(limit, result.size()));
    }

    @Override
    public boolean close(IdempotencyKey key, String outcome) {
      if (open.remove(key) == null) {
        return false;
      }
      outcomes.put(key, outcome);
      return true;
    }
  }

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final FakeIndex index = new FakeIndex();
  private final DeadlineEvaluator evaluator =
      new DeadlineEvaluator(
          registry,
          index,
          Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC),
          Map.of(Lane.FAST, FAST, Lane.STANDARD, STANDARD),
          Duration.ofSeconds(15),
          500);

  private double events(String lane, String outcome) {
    return registry
        .get("slo.j2.events")
        .tag("lane", lane)
        .tag("outcome", outcome)
        .counter()
        .count();
  }

  @Test
  void classificaSomentePropostasComPrazoVencido() {
    long due = NOW - FAST.toMillis();
    index.add(Lane.FAST, due, due + FAST.toMillis()); // decidida exatamente no prazo: boa
    index.add(Lane.FAST, due - 1, due + FAST.toMillis()); // decidida 1 ms depois do prazo: ruim
    index.add(Lane.FAST, due - 60_000, null); // vencida sem decisao: ruim
    index.add(Lane.FAST, due + 1, null); // ainda no prazo: fica aberta

    evaluator.evaluateNow();

    assertThat(events("fast", "good")).isEqualTo(1);
    assertThat(events("fast", "bad")).isEqualTo(2);
    assertThat(index.open).hasSize(1);
  }

  @Test
  void usaOPrazoDaPropriaLane() {
    long tenMinutesAgo = NOW - Duration.ofMinutes(10).toMillis();
    index.add(Lane.STANDARD, tenMinutesAgo, null); // standard: 45 min, ainda no prazo
    index.add(Lane.FAST, tenMinutesAgo, tenMinutesAgo + 30_000); // fast: decidida em 30 s

    evaluator.evaluateNow();

    assertThat(events("standard", "good") + events("standard", "bad")).isZero();
    assertThat(events("fast", "good")).isEqualTo(1);
  }

  @Test
  void contaCadaPropostaUmaUnicaVez() {
    index.add(Lane.STANDARD, NOW - STANDARD.toMillis() - 1, null);

    evaluator.evaluateNow();
    evaluator.evaluateNow();

    assertThat(events("standard", "bad")).isEqualTo(1);
  }

  @Test
  void naoContaQuandoOutraReplicaFechouAntes() {
    DeadlineIndexPort racing =
        new DeadlineIndexPort() {
          @Override
          public List<OpenProposal> acceptedUpTo(Lane lane, long cutoff, int limit) {
            return lane == Lane.FAST
                ? List.of(
                    new OpenProposal(IdempotencyKey.parse(UUID.randomUUID().toString()), 0L, null))
                : List.of();
          }

          @Override
          public boolean close(IdempotencyKey key, String outcome) {
            return false;
          }
        };
    new DeadlineEvaluator(
            registry,
            racing,
            Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC),
            Map.of(Lane.FAST, FAST, Lane.STANDARD, STANDARD),
            Duration.ofSeconds(15),
            500)
        .evaluateNow();

    assertThat(events("fast", "bad")).isZero();
  }

  @Test
  void publicaOInstanteDoUltimoCicloCompleto() {
    assertThat(registry.get("slo.j2.last.evaluation").gauge().value()).isZero();

    evaluator.evaluateNow();

    assertThat(registry.get("slo.j2.last.evaluation").gauge().value()).isEqualTo(NOW / 1000.0);
  }
}
