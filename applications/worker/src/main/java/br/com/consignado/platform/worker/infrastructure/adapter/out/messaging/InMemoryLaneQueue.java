package br.com.consignado.platform.worker.infrastructure.adapter.out.messaging;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.contract.ProposalMessage;
import br.com.consignado.platform.worker.application.port.out.LaneQueuePort;
import br.com.consignado.platform.worker.application.port.out.QueueDepthPort;
import br.com.consignado.platform.worker.application.port.out.QueuedMessage;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Filas em memoria, <b>apenas para desenvolvimento e testes</b>. Imita o redrive do SQS: apos
 * {@code maxReceiveCount} entregas sem conclusao, a mensagem vai para a lista de mensagens mortas.
 * O atraso de {@code release} e ignorado (a mensagem volta imediatamente).
 */
public final class InMemoryLaneQueue implements LaneQueuePort, QueueDepthPort {

  private record MemoryMessage(Lane lane, ProposalMessage message, int receiveCount)
      implements QueuedMessage {}

  private final int maxReceiveCount;
  private final ConcurrentLinkedQueue<MemoryMessage> fast = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<MemoryMessage> standard = new ConcurrentLinkedQueue<>();
  private final List<ProposalMessage> deadLetters = new CopyOnWriteArrayList<>();
  private final AtomicInteger acknowledged = new AtomicInteger();

  public InMemoryLaneQueue(int maxReceiveCount) {
    this.maxReceiveCount = maxReceiveCount;
  }

  public void enqueue(Lane lane, ProposalMessage message) {
    queue(lane).add(new MemoryMessage(lane, message, 0));
  }

  @Override
  public boolean hasMessages(Lane lane) {
    return !queue(lane).isEmpty();
  }

  @Override
  public Optional<QueuedMessage> poll(Lane lane) {
    MemoryMessage next = queue(lane).poll();
    if (next == null) {
      return Optional.empty();
    }
    return Optional.of(new MemoryMessage(next.lane(), next.message(), next.receiveCount() + 1));
  }

  @Override
  public void acknowledge(QueuedMessage message) {
    acknowledged.incrementAndGet();
  }

  @Override
  public void release(QueuedMessage message, Duration delay) {
    if (message.receiveCount() >= maxReceiveCount) {
      deadLetters.add(message.message());
    } else {
      queue(message.lane())
          .add(new MemoryMessage(message.lane(), message.message(), message.receiveCount()));
    }
  }

  @Override
  public long approximateDepth(Lane lane) {
    return queue(lane).size();
  }

  public List<ProposalMessage> deadLetters() {
    return List.copyOf(deadLetters);
  }

  public int acknowledgedCount() {
    return acknowledged.get();
  }

  private ConcurrentLinkedQueue<MemoryMessage> queue(Lane lane) {
    return lane == Lane.FAST ? fast : standard;
  }
}
