package br.com.consignado.platform.worker.application.port.out;

/** Estado do orcamento: teto global e piso de cada lane. */
public record BudgetSnapshot(boolean globalReady, LaneBudget fast, LaneBudget standard) {

  /** Piso de uma lane: tem permissao agora e quanto "credito" acumulou. */
  public record LaneBudget(boolean floorReady, double credit) {}
}
