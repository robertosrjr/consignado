package br.com.consignado.platform.worker.infrastructure.adapter.out.budget;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.worker.application.port.out.BudgetSnapshot;
import br.com.consignado.platform.worker.application.port.out.ThroughputBudgetPort;
import br.com.consignado.platform.worker.domain.TokenBucket;
import java.util.function.LongSupplier;

/**
 * Orcamento fixo por tarefa: o teto global do legado (180 TPS) dividido pelo numero de instancias
 * do Worker, reduzido por uma margem de seguranca, e repartido em pisos por lane (70% Fast / 30%
 * Standard). Cada permissao vem de um balde de capacidade pequena; assim nenhuma janela de 1 s
 * ultrapassa {@code capacidade + taxa}, mantendo a soma abaixo do limite do legado.
 *
 * <p><b>Limite documentado:</b> a protecao e local ao processo; se o numero real de instancias
 * diferir de {@code instances}, o teto efetivo desvia (o Mock Legacy mede o pico real).
 */
public final class FixedPerTaskBudgetAdapter implements ThroughputBudgetPort {

  private final TokenBucket global;
  private final TokenBucket fast;
  private final TokenBucket standard;

  public FixedPerTaskBudgetAdapter(
      double totalTps,
      int instances,
      double fastShare,
      double safetyMargin,
      double bucketCapacity,
      LongSupplier nanoClock) {
    if (totalTps <= 0 || instances < 1 || fastShare <= 0 || fastShare >= 1) {
      throw new IllegalArgumentException("Orcamento invalido");
    }
    if (safetyMargin <= 0 || safetyMargin > 1) {
      throw new IllegalArgumentException("Margem de seguranca invalida");
    }
    double perInstance = totalTps * safetyMargin / instances;
    this.global = new TokenBucket(perInstance, bucketCapacity, nanoClock);
    this.fast = new TokenBucket(perInstance * fastShare, bucketCapacity, nanoClock);
    this.standard = new TokenBucket(perInstance * (1 - fastShare), bucketCapacity, nanoClock);
  }

  @Override
  public BudgetSnapshot snapshot() {
    double fastTokens = fast.tokens();
    double standardTokens = standard.tokens();
    return new BudgetSnapshot(
        global.tokens() >= 1.0,
        new BudgetSnapshot.LaneBudget(fastTokens >= 1.0, fastTokens),
        new BudgetSnapshot.LaneBudget(standardTokens >= 1.0, standardTokens));
  }

  @Override
  public void consume(Lane lane) {
    global.tryConsume();
    // o piso da lane so e debitado quando disponivel; sem ele, a lane esta usando capacidade
    // emprestada
    (lane == Lane.FAST ? fast : standard).tryConsume();
  }
}
