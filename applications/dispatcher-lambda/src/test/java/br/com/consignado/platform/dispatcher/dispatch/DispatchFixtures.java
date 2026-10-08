package br.com.consignado.platform.dispatcher.dispatch;

import br.com.consignado.platform.dispatcher.routing.LaneRouter;
import br.com.consignado.platform.dispatcher.routing.PriorityRules;
import br.com.consignado.platform.dispatcher.routing.PriorityScorer;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.contract.ProposalMessage;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Fakes e construtores de dados dos testes do despacho. */
final class DispatchFixtures {

  static final String KEY = "e3b0c442-98fc-11ee-b9d1-0242ac120002";

  private DispatchFixtures() {}

  record Published(Lane lane, ProposalMessage message, String tracingId) {}

  static final class FakePublisher implements QueuePublisher {
    final List<Published> published = new ArrayList<>();
    RuntimeException failure;

    @Override
    public void publish(Lane lane, ProposalMessage message, String tracingId) {
      if (failure != null) {
        throw failure;
      }
      published.add(new Published(lane, message, tracingId));
    }
  }

  static final class FakeUpdater implements InboxStatusUpdater {
    final List<IdempotencyKey> marked = new ArrayList<>();
    RuntimeException failure;

    @Override
    public void markDispatched(IdempotencyKey key, int priorityScore) {
      if (failure != null) {
        throw failure;
      }
      marked.add(key);
    }
  }

  static DispatchService service(FakePublisher publisher, FakeUpdater updater) {
    PriorityRules rules = PriorityRules.withDefaults(Set.of("vip-bank"), Money.of("50000.00"));
    return new DispatchService(
        new PriorityScorer(rules), new LaneRouter(rules.fastThreshold()), publisher, updater);
  }

  static StreamRecordView insert(
      String sequence, String key, String tenant, boolean portability, String amount) {
    return new StreamRecordView(
        "INSERT",
        sequence,
        key,
        tenant,
        "INSS",
        portability,
        amount,
        "trace-" + sequence,
        "RECEIVED");
  }

  static StreamRecordView insert(String sequence) {
    return insert(sequence, KEY, "banco-a", true, "1000.00");
  }
}
