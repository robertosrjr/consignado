package br.com.consignado.platform.dispatcher.dispatch;

import br.com.consignado.platform.dispatcher.routing.LaneRouter;
import br.com.consignado.platform.dispatcher.routing.PriorityScorer;
import br.com.consignado.platform.dispatcher.routing.RoutingAttributes;
import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.contract.InboxAttributes;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.contract.ProposalMessage;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.shared.vo.TenantId;

/**
 * Despacha um registro do Stream: calcula o escore, escolhe a lane, publica no SQS e marca o item
 * como DISPATCHED. A ordem (publicar antes de marcar) garante entrega ao-menos-uma-vez; o Worker e
 * idempotente, entao uma republicacao apos falha parcial e inofensiva.
 */
public final class DispatchService {

  private final PriorityScorer scorer;
  private final LaneRouter router;
  private final QueuePublisher publisher;
  private final InboxStatusUpdater inbox;

  public DispatchService(
      PriorityScorer scorer,
      LaneRouter router,
      QueuePublisher publisher,
      InboxStatusUpdater inbox) {
    this.scorer = scorer;
    this.router = router;
    this.publisher = publisher;
    this.inbox = inbox;
  }

  /**
   * @throws IllegalArgumentException se o item estiver malformado (registro "veneno")
   * @throws RuntimeException se a publicacao ou a atualizacao falhar (o Stream repete)
   */
  public DispatchOutcome dispatch(StreamRecordView record) {
    if (!record.isInsert()) {
      return DispatchOutcome.skipped();
    }
    if (!InboxAttributes.STATUS_RECEIVED.equals(record.status())) {
      return DispatchOutcome.skipped();
    }
    IdempotencyKey key = IdempotencyKey.parse(required(record.idempotencyKey()));
    TenantId tenant = new TenantId(required(record.tenantId()));
    RoutingAttributes attributes =
        new RoutingAttributes(
            tenant,
            ConvenioType.valueOf(required(record.convenioType())),
            Boolean.TRUE.equals(record.portability()),
            Money.of(required(record.amount())));

    int score = scorer.score(attributes);
    Lane lane = router.route(score);
    publisher.publish(
        lane,
        ProposalMessage.of(key, tenant, lane, score),
        record.tracingId(),
        record.traceContext());
    inbox.markDispatched(key, score, lane);
    return DispatchOutcome.dispatched(lane);
  }

  private static String required(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Registro de stream incompleto");
    }
    return value;
  }
}
