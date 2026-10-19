package br.com.consignado.platform.worker.application.usecase;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.worker.application.port.in.ProcessProposalUseCase;
import br.com.consignado.platform.worker.application.port.in.ProcessingOutcome;
import br.com.consignado.platform.worker.application.port.out.BudgetSnapshot;
import br.com.consignado.platform.worker.application.port.out.DispatchObserver;
import br.com.consignado.platform.worker.application.port.out.LaneQueuePort;
import br.com.consignado.platform.worker.application.port.out.LegacyAvailabilityPort;
import br.com.consignado.platform.worker.application.port.out.QueuedMessage;
import br.com.consignado.platform.worker.application.port.out.ThroughputBudgetPort;
import br.com.consignado.platform.worker.domain.LaneBudgetAllocator;
import br.com.consignado.platform.worker.domain.LaneBudgetAllocator.LaneView;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * Laco de despacho: a cada passo escolhe a lane pelo orcamento (piso 70/30 com emprestimo e teto do
 * legado), retira uma mensagem, consome a permissao e processa em outra thread (virtual), com
 * concorrencia limitada. Conclui a mensagem em {@code COMPLETED}; nos demais casos devolve a fila
 * com backoff (o redrive do SQS limita as tentativas e envia a DLQ). Com o legado indisponivel
 * (circuito aberto), para de retirar mensagens: a protecao local nao gasta tentativas de entrega.
 */
public final class ProposalDispatchLoop {

  private static final Logger LOG = System.getLogger(ProposalDispatchLoop.class.getName());
  private static final long IDLE_PARK_NANOS = 200_000L;

  private final LaneQueuePort queue;
  private final ThroughputBudgetPort budget;
  private final ProcessProposalUseCase useCase;
  private final LaneBudgetAllocator allocator;
  private final Executor executor;
  private final Semaphore inFlight;
  private final int maxInFlight;
  private final RetryBackoff backoff;
  private final DispatchObserver observer;
  private final LegacyAvailabilityPort legacy;

  public ProposalDispatchLoop(
      LaneQueuePort queue,
      ThroughputBudgetPort budget,
      ProcessProposalUseCase useCase,
      LaneBudgetAllocator allocator,
      Executor executor,
      int maxInFlight,
      RetryBackoff backoff,
      DispatchObserver observer) {
    this(
        queue,
        budget,
        useCase,
        allocator,
        executor,
        maxInFlight,
        backoff,
        observer,
        LegacyAvailabilityPort.ALWAYS_AVAILABLE);
  }

  public ProposalDispatchLoop(
      LaneQueuePort queue,
      ThroughputBudgetPort budget,
      ProcessProposalUseCase useCase,
      LaneBudgetAllocator allocator,
      Executor executor,
      int maxInFlight,
      RetryBackoff backoff,
      DispatchObserver observer,
      LegacyAvailabilityPort legacy) {
    if (maxInFlight < 1) {
      throw new IllegalArgumentException("Concorrencia maxima invalida");
    }
    this.queue = queue;
    this.budget = budget;
    this.useCase = useCase;
    this.allocator = allocator;
    this.executor = executor;
    this.inFlight = new Semaphore(maxInFlight);
    this.maxInFlight = maxInFlight;
    this.backoff = backoff;
    this.observer = observer;
    this.legacy = legacy;
  }

  /**
   * Mensagens em processamento neste instante (saturacao). Limitado por {@link #maxInFlight()}: ao
   * atingir o limite, o laco para de retirar mensagens, ainda que haja orcamento de vazao.
   */
  public int inFlight() {
    return maxInFlight - inFlight.availablePermits();
  }

  /** Concorrencia maxima configurada ({@code worker.max-in-flight}). */
  public int maxInFlight() {
    return maxInFlight;
  }

  /** Executa ate {@code running} ficar falso, dormindo brevemente quando nada pode ser atendido. */
  public void run(BooleanSupplier running) {
    while (running.getAsBoolean()) {
      if (!runOnce()) {
        LockSupport.parkNanos(IDLE_PARK_NANOS);
      }
    }
  }

  /** Um passo do laco; retorna {@code true} se uma mensagem foi despachada. */
  public boolean runOnce() {
    if (!legacyAccepting()) {
      return false;
    }
    if (!inFlight.tryAcquire()) {
      return false;
    }
    boolean started = false;
    try {
      BudgetSnapshot snapshot = budget.snapshot();
      Optional<Lane> pick =
          allocator.pick(
              view(Lane.FAST, snapshot.fast()),
              view(Lane.STANDARD, snapshot.standard()),
              snapshot.globalReady());
      if (pick.isEmpty()) {
        return false;
      }
      Optional<QueuedMessage> polled = queue.poll(pick.get());
      if (polled.isEmpty()) {
        return false;
      }
      budget.consume(pick.get());
      QueuedMessage message = polled.get();
      observer.dispatched(message.lane());
      executor.execute(message.processingTask(() -> handle(message)));
      started = true;
      return true;
    } finally {
      if (!started) {
        inFlight.release();
      }
    }
  }

  /**
   * Back-pressure: com o legado indisponivel nada sai da fila; em teste, uma chamada de prova por
   * vez. O laco e a unica thread que despacha, entao a checagem de {@code inFlight()} nao disputa.
   */
  private boolean legacyAccepting() {
    return switch (legacy.availability()) {
      case AVAILABLE -> true;
      case PROBING -> inFlight() == 0;
      case UNAVAILABLE -> false;
    };
  }

  private LaneView view(Lane lane, BudgetSnapshot.LaneBudget laneBudget) {
    return new LaneView(queue.hasMessages(lane), laneBudget.floorReady(), laneBudget.credit());
  }

  private void handle(QueuedMessage message) {
    try {
      ProcessingOutcome outcome = useCase.process(message.message());
      observer.processed(message.lane(), outcome);
      if (outcome == ProcessingOutcome.COMPLETED) {
        queue.acknowledge(message);
      } else {
        queue.release(message, backoff.delay(message.receiveCount()));
      }
    } catch (RuntimeException e) {
      message.processingFailed(e);
      observer.failed(message.lane());
      LOG.log(
          Level.ERROR,
          "event=processing_failed lane={0} error={1}",
          message.lane(),
          e.getClass().getSimpleName());
      safeRelease(message);
    } finally {
      inFlight.release();
    }
  }

  private void safeRelease(QueuedMessage message) {
    try {
      queue.release(message, backoff.delay(message.receiveCount()));
    } catch (RuntimeException e) {
      LOG.log(Level.ERROR, "event=release_failed lane={0}", message.lane());
    }
  }
}
