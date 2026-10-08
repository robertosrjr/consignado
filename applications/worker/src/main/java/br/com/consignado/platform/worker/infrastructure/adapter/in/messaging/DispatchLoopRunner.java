package br.com.consignado.platform.worker.infrastructure.adapter.in.messaging;

import br.com.consignado.platform.worker.application.usecase.ProposalDispatchLoop;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.context.SmartLifecycle;

/**
 * Adaptador de entrada: executa o laco de despacho em uma thread propria e o encerra de forma
 * graciosa (termina os processamentos em andamento antes de parar).
 */
public final class DispatchLoopRunner implements SmartLifecycle {

  private static final long SHUTDOWN_WAIT_SECONDS = 20;

  private final ProposalDispatchLoop loop;
  private final ExecutorService executor;
  private Thread thread;
  private volatile boolean running;

  public DispatchLoopRunner(ProposalDispatchLoop loop, ExecutorService executor) {
    this.loop = loop;
    this.executor = executor;
  }

  @Override
  public void start() {
    running = true;
    thread = Thread.ofPlatform().name("dispatch-loop").start(() -> loop.run(() -> running));
  }

  @Override
  public void stop() {
    running = false;
    try {
      if (thread != null) {
        thread.join(TimeUnit.SECONDS.toMillis(SHUTDOWN_WAIT_SECONDS));
      }
      executor.shutdown();
      executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    return Integer.MAX_VALUE;
  }
}
