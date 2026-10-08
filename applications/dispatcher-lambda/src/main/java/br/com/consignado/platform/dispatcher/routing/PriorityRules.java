package br.com.consignado.platform.dispatcher.routing;

import br.com.consignado.platform.shared.vo.Money;
import java.util.Set;

/**
 * Regras de prioridade (D10): portabilidade OU parceiro VIP OU valor acima do limiar. Cada criterio
 * soma um peso (0 a 100); o escore e limitado a 100. Com {@code fastThreshold} menor ou igual ao
 * menor peso, qualquer criterio isolado leva a Fast Lane.
 */
public record PriorityRules(
    Set<String> vipTenants,
    Money amountThreshold,
    int portabilityWeight,
    int vipWeight,
    int amountWeight,
    int fastThreshold) {

  public static final int DEFAULT_PORTABILITY_WEIGHT = 60;
  public static final int DEFAULT_VIP_WEIGHT = 40;
  public static final int DEFAULT_AMOUNT_WEIGHT = 30;
  public static final int DEFAULT_FAST_THRESHOLD = 30;

  public PriorityRules {
    vipTenants = Set.copyOf(vipTenants);
    if (amountThreshold == null) {
      throw new IllegalArgumentException("Limiar de valor ausente");
    }
    requireWeight(portabilityWeight);
    requireWeight(vipWeight);
    requireWeight(amountWeight);
    if (fastThreshold < 1 || fastThreshold > 100) {
      throw new IllegalArgumentException("Limiar da Fast Lane invalido");
    }
  }

  public static PriorityRules withDefaults(Set<String> vipTenants, Money amountThreshold) {
    return new PriorityRules(
        vipTenants,
        amountThreshold,
        DEFAULT_PORTABILITY_WEIGHT,
        DEFAULT_VIP_WEIGHT,
        DEFAULT_AMOUNT_WEIGHT,
        DEFAULT_FAST_THRESHOLD);
  }

  private static void requireWeight(int weight) {
    if (weight < 0 || weight > 100) {
      throw new IllegalArgumentException("Peso de prioridade invalido");
    }
  }
}
