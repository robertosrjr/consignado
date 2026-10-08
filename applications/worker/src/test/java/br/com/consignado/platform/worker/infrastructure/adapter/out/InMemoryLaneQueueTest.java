package br.com.consignado.platform.worker.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.contract.ProposalMessage;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;
import br.com.consignado.platform.worker.application.port.out.QueuedMessage;
import br.com.consignado.platform.worker.infrastructure.adapter.out.messaging.InMemoryLaneQueue;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryLaneQueueTest {

  private final InMemoryLaneQueue queue = new InMemoryLaneQueue(3);

  private static ProposalMessage message(Lane lane) {
    return ProposalMessage.of(
        new IdempotencyKey(UUID.randomUUID()), new TenantId("banco-a"), lane, 5);
  }

  @Test
  void should_deliver_per_lane_with_a_receive_count_starting_at_one() {
    queue.enqueue(Lane.FAST, message(Lane.FAST));

    assertThat(queue.hasMessages(Lane.FAST)).isTrue();
    assertThat(queue.hasMessages(Lane.STANDARD)).isFalse();
    QueuedMessage polled = queue.poll(Lane.FAST).orElseThrow();
    assertThat(polled.receiveCount()).isEqualTo(1);
    assertThat(queue.hasMessages(Lane.FAST)).isFalse();
    assertThat(queue.poll(Lane.STANDARD)).isEmpty();
  }

  @Test
  void should_requeue_released_messages_keeping_the_receive_count() {
    queue.enqueue(Lane.STANDARD, message(Lane.STANDARD));
    QueuedMessage first = queue.poll(Lane.STANDARD).orElseThrow();

    queue.release(first, Duration.ofSeconds(5));

    QueuedMessage second = queue.poll(Lane.STANDARD).orElseThrow();
    assertThat(second.receiveCount()).isEqualTo(2);
    assertThat(second.message()).isEqualTo(first.message());
  }

  @Test
  void should_move_to_the_dead_letters_after_the_maximum_number_of_receives() {
    ProposalMessage message = message(Lane.FAST);
    queue.enqueue(Lane.FAST, message);

    for (int i = 0; i < 3; i++) {
      queue.release(queue.poll(Lane.FAST).orElseThrow(), Duration.ZERO);
    }

    assertThat(queue.poll(Lane.FAST)).isEmpty();
    assertThat(queue.deadLetters()).containsExactly(message);
  }

  @Test
  void should_count_acknowledged_messages_and_report_the_depth() {
    queue.enqueue(Lane.FAST, message(Lane.FAST));
    queue.enqueue(Lane.FAST, message(Lane.FAST));

    assertThat(queue.approximateDepth(Lane.FAST)).isEqualTo(2);
    queue.acknowledge(queue.poll(Lane.FAST).orElseThrow());

    assertThat(queue.acknowledgedCount()).isEqualTo(1);
    assertThat(queue.approximateDepth(Lane.FAST)).isEqualTo(1);
  }
}
