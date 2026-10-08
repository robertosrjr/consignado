package br.com.consignado.platform.dispatcher.routing;

/** Calcula o {@code priorityScore} (0 a 100) de forma pura e deterministica. */
public final class PriorityScorer {

  private final PriorityRules rules;

  public PriorityScorer(PriorityRules rules) {
    this.rules = rules;
  }

  public int score(RoutingAttributes attributes) {
    int total = 0;
    if (attributes.portability()) {
      total += rules.portabilityWeight();
    }
    if (rules.vipTenants().contains(attributes.tenant().value())) {
      total += rules.vipWeight();
    }
    // valor >= limiar (inclusivo)
    if (!rules.amountThreshold().isGreaterThan(attributes.amount())) {
      total += rules.amountWeight();
    }
    return Math.min(100, total);
  }
}
