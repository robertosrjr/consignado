package br.com.consignado.platform.worker.domain;

import br.com.consignado.platform.shared.contract.Lane;
import java.util.Optional;

/**
 * Escolhe a lane a atender respeitando o orcamento global: piso garantido por lane (70% Fast / 30%
 * Standard) e emprestimo da capacidade ociosa quando a outra lane nao tem mensagens. Funcao pura.
 */
public final class LaneBudgetAllocator {

  /** Estado de uma lane: ha mensagens, o piso tem permissao e o "credito" acumulado do piso. */
  public record LaneView(boolean hasMessages, boolean floorReady, double credit) {}

  /**
   * @param globalReady o orcamento global (teto do legado) tem permissao
   * @return a lane a atender agora, ou vazio se nenhuma pode ser atendida
   */
  public Optional<Lane> pick(LaneView fast, LaneView standard, boolean globalReady) {
    if (!globalReady) {
      return Optional.empty();
    }
    boolean fastEligible = eligible(fast, standard);
    boolean standardEligible = eligible(standard, fast);
    if (fastEligible && standardEligible) {
      // ambas com mensagens e piso disponivel: atende a de maior credito acumulado (empate: FAST)
      return Optional.of(standard.credit() > fast.credit() ? Lane.STANDARD : Lane.FAST);
    }
    if (fastEligible) {
      return Optional.of(Lane.FAST);
    }
    if (standardEligible) {
      return Optional.of(Lane.STANDARD);
    }
    return Optional.empty();
  }

  /** Atendivel se tem mensagens e (piso disponivel ou a outra lane esta vazia: emprestimo). */
  private static boolean eligible(LaneView lane, LaneView other) {
    return lane.hasMessages() && (lane.floorReady() || !other.hasMessages());
  }
}
