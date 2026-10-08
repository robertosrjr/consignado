package br.com.consignado.platform.shared.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Valor monetario em BRL: nao negativo, escala 2, limitado a NUMERIC(12,2). Valores com mais de 2
 * casas sao rejeitados (sem arredondamento silencioso de dinheiro).
 */
public record Money(BigDecimal amount) {

  public static final BigDecimal MAX = new BigDecimal("9999999999.99");
  private static final int SCALE = 2;

  public Money {
    if (amount == null) {
      throw new IllegalArgumentException("Valor invalido");
    }
    if (amount.signum() < 0 || amount.stripTrailingZeros().scale() > SCALE) {
      throw new IllegalArgumentException("Valor invalido");
    }
    amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    if (amount.compareTo(MAX) > 0) {
      throw new IllegalArgumentException("Valor invalido");
    }
  }

  public static Money of(String raw) {
    if (raw == null) {
      throw new IllegalArgumentException("Valor invalido");
    }
    try {
      return new Money(new BigDecimal(raw));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Valor invalido", e);
    }
  }

  public Money add(Money other) {
    return new Money(amount.add(other.amount));
  }

  public boolean isGreaterThan(Money other) {
    return amount.compareTo(other.amount) > 0;
  }
}
