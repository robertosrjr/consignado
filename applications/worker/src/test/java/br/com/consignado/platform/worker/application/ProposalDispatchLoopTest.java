package br.com.consignado.platform.worker.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.contract.ProposalMessage;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.worker.application.WorkerFixtures.TestMessage;
import br.com.consignado.platform.worker.application.port.in.ProcessingOutcome;
import br.com.consignado.platform.worker.application.port.out.BudgetSnapshot;
import br.com.consignado.platform.worker.application.port.out.DispatchObserver;
import br.com.consignado.platform.worker.application.port.out.LaneQueuePort;
import br.com.consignado.platform.worker.application.port.out.QueuedMessage;
import br.com.consignado.platform.worker.application.port.out.ThroughputBudgetPort;
import br.com.consignado.platform.worker.application.usecase.ProposalDispatchLoop;
import br.com.consignado.platform.worker.application.usecase.RetryBackoff;
import br.com.consignado.platform.worker.domain.LaneBudgetAllocator;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ProposalDispatchLoopTest {

  private static final class FakeQueue implements LaneQueuePort {
    final Map<Lane, Deque<QueuedMessage>> messages = new EnumMap<>(Lane.class);
    final List<QueuedMessage> acknowledged = new ArrayList<>();
    final List<Duration> releaseDelays = new ArrayList<>();
    final List<QueuedMessage> released = new ArrayList<>();
    int polls;

    FakeQueue() {
      messages.put(Lane.FAST, new ArrayDeque<>());
      messages.put(Lane.STANDARD, new ArrayDeque<>());
    }

    void add(Lane lane, int receiveCount) {
      ProposalMessage message =
          ProposalMessage.of(
              new IdempotencyKey(UUID.randomUUID()),
              WorkerFixtures.TENANT,
              lane,
              lane == Lane.FAST ? 60 : 0);
      messages.get(lane).add(new TestMessage(lane, message, receiveCount));
    }

    @Override
    public boolean hasMessages(Lane lane) {
      return !messages.get(lane).isEmpty();
    }

    @Override
    public Optional<QueuedMessage> poll(Lane lane) {
      polls++;
      return Optional.ofNullable(messages.get(lane).poll());
    }

    @Override
    public void acknowledge(QueuedMessage message) {
      acknowledged.add(message);
    }

    @Override
    public void release(QueuedMessage message, Duration delay) {
      released.add(message);
      releaseDelays.add(delay);
    }
  }

  private static final class FakeBudget implements ThroughputBudgetPort {
    boolean ready = true;
    final List<Lane> consumed = new ArrayList<>();

    @Override
    public BudgetSnapshot snapshot() {
      return new BudgetSnapshot(
          ready, new BudgetSnapshot.LaneBudget(ready, 2), new BudgetSnapshot.LaneBudget(ready, 2));
    }

    @Override
    public void consume(Lane lane) {
      consumed.add(lane);
    }
  }

  private static final class RecordingObserver implements DispatchObserver {
    final List<String> events = new ArrayList<>();

    @Override
    public void dispatched(Lane lane) {
      events.add("dispatched:" + lane);
    }

    @Override
    public void processed(Lane lane, ProcessingOutcome outcome) {
      events.add("processed:" + lane + ":" + outcome);
    }

    @Override
    public void failed(Lane lane) {
      events.add("failed:" + lane);
    }
  }

  private final FakeQueue queue = new FakeQueue();
  private final FakeBudget budget = new FakeBudget();
  private final RecordingObserver observer = new RecordingObserver();
  private Function<ProposalMessage, ProcessingOutcome> behavior = m -> ProcessingOutcome.COMPLETED;

  private ProposalDispatchLoop loop(Executor executor, int maxInFlight) {
    return new ProposalDispatchLoop(
        queue,
        budget,
        message -> behavior.apply(message),
        new LaneBudgetAllocator(),
        executor,
        maxInFlight,
        new RetryBackoff(Duration.ofSeconds(5), Duration.ofSeconds(60)),
        observer);
  }

  @Test
  void should_dispatch_a_message_consume_the_budget_and_acknowledge_on_completion() {
    queue.add(Lane.FAST, 1);
    ProposalDispatchLoop loop = loop(Runnable::run, 4);

    assertThat(loop.runOnce()).isTrue();

    assertThat(budget.consumed).containsExactly(Lane.FAST);
    assertThat(queue.acknowledged).hasSize(1);
    assertThat(queue.released).isEmpty();
    assertThat(observer.events).containsExactly("dispatched:FAST", "processed:FAST:COMPLETED");
  }

  @Test
  void should_release_with_exponential_backoff_on_retry() {
    queue.add(Lane.STANDARD, 1);
    queue.add(Lane.STANDARD, 2);
    queue.add(Lane.STANDARD, 3);
    behavior = m -> ProcessingOutcome.RETRY;
    ProposalDispatchLoop loop = loop(Runnable::run, 4);

    loop.runOnce();
    loop.runOnce();
    loop.runOnce();

    assertThat(queue.releaseDelays)
        .containsExactly(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(20));
    assertThat(queue.acknowledged).isEmpty();
  }

  @Test
  void should_release_unprocessable_messages_so_the_redrive_sends_them_to_the_dlq() {
    queue.add(Lane.FAST, 1);
    behavior = m -> ProcessingOutcome.UNPROCESSABLE;

    loop(Runnable::run, 4).runOnce();

    assertThat(queue.released).hasSize(1);
    assertThat(queue.acknowledged).isEmpty();
    assertThat(observer.events).contains("processed:FAST:UNPROCESSABLE");
  }

  @Test
  void should_release_and_keep_running_when_processing_throws() {
    queue.add(Lane.FAST, 1);
    queue.add(Lane.FAST, 1);
    behavior =
        m -> {
          throw new IllegalStateException("falha inesperada");
        };
    ProposalDispatchLoop loop = loop(Runnable::run, 1);

    assertThat(loop.runOnce()).isTrue();
    assertThat(loop.runOnce()).isTrue();

    assertThat(queue.released).hasSize(2);
    assertThat(observer.events).contains("failed:FAST");
  }

  @Test
  void should_not_touch_the_queue_when_the_budget_is_not_ready() {
    queue.add(Lane.FAST, 1);
    budget.ready = false;

    assertThat(loop(Runnable::run, 4).runOnce()).isFalse();

    assertThat(queue.polls).isZero();
    assertThat(budget.consumed).isEmpty();
  }

  @Test
  void should_report_idle_when_there_are_no_messages() {
    assertThat(loop(Runnable::run, 4).runOnce()).isFalse();
    assertThat(budget.consumed).isEmpty();
  }

  @Test
  void should_prefer_the_fast_lane_when_both_lanes_are_ready_and_tied() {
    queue.add(Lane.STANDARD, 1);
    queue.add(Lane.FAST, 1);
    ProposalDispatchLoop loop = loop(Runnable::run, 4);

    loop.runOnce();

    assertThat(budget.consumed).containsExactly(Lane.FAST);
  }

  @Test
  void should_limit_the_number_of_messages_in_flight() {
    for (int i = 0; i < 4; i++) {
      queue.add(Lane.FAST, 1);
    }
    List<Runnable> pending = new ArrayList<>();
    ProposalDispatchLoop loop = loop(pending::add, 2);

    assertThat(loop.runOnce()).isTrue();
    assertThat(loop.runOnce()).isTrue();
    assertThat(loop.runOnce()).as("limite de concorrencia atingido").isFalse();

    pending.get(0).run();
    assertThat(loop.runOnce()).isTrue();
  }

  @Test
  void should_report_the_in_flight_count_as_saturation() {
    for (int i = 0; i < 3; i++) {
      queue.add(Lane.FAST, 1);
    }
    List<Runnable> pending = new ArrayList<>();
    ProposalDispatchLoop loop = loop(pending::add, 2);

    assertThat(loop.inFlight()).isZero();
    assertThat(loop.maxInFlight()).isEqualTo(2);

    loop.runOnce();
    assertThat(loop.inFlight()).isEqualTo(1);
    loop.runOnce();
    assertThat(loop.inFlight()).as("saturado: o limite de concorrencia e o gargalo").isEqualTo(2);

    pending.get(0).run();
    assertThat(loop.inFlight()).isEqualTo(1);
  }

  @Test
  void should_stop_the_run_loop_when_the_running_flag_turns_false() {
    queue.add(Lane.FAST, 1);
    int[] checks = {0};
    ProposalDispatchLoop loop = loop(Runnable::run, 4);

    loop.run(() -> ++checks[0] <= 3);

    assertThat(queue.acknowledged).hasSize(1);
  }

  @Test
  void should_reject_an_invalid_concurrency_limit() {
    assertThatThrownBy(() -> loop(Runnable::run, 0)).isInstanceOf(IllegalArgumentException.class);
  }
}
