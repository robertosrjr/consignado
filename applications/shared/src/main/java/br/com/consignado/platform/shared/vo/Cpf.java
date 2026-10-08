package br.com.consignado.platform.shared.vo;

/**
 * CPF validado. O valor completo so e acessivel por {@link #digits()}; {@link #toString()} e sempre
 * mascarado para evitar PII em logs, metricas e traces.
 */
public record Cpf(String digits) {

  private static final int LENGTH = 11;

  public Cpf {
    if (digits == null || digits.length() != LENGTH || !isAllDigits(digits)) {
      throw new IllegalArgumentException("CPF invalido");
    }
    if (allSameDigit(digits) || !checkDigitsMatch(digits)) {
      throw new IllegalArgumentException("CPF invalido");
    }
  }

  /** Aceita apenas digitos, com pontos e hifen opcionais; qualquer outro caractere e rejeitado. */
  public static Cpf of(String raw) {
    if (raw == null) {
      throw new IllegalArgumentException("CPF invalido");
    }
    StringBuilder normalized = new StringBuilder(LENGTH);
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '.' || c == '-') {
        continue;
      }
      normalized.append(c);
    }
    return new Cpf(normalized.toString());
  }

  /** Forma mascarada, segura para log. */
  @Override
  public String toString() {
    return "***.***.***-" + digits.substring(LENGTH - 2);
  }

  private static boolean isAllDigits(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  private static boolean allSameDigit(String value) {
    char first = value.charAt(0);
    for (int i = 1; i < value.length(); i++) {
      if (value.charAt(i) != first) {
        return false;
      }
    }
    return true;
  }

  private static boolean checkDigitsMatch(String value) {
    return checkDigit(value, 9) == value.charAt(9) - '0'
        && checkDigit(value, 10) == value.charAt(10) - '0';
  }

  /** Calcula o digito verificador sobre os primeiros {@code length} digitos. */
  static int checkDigit(String value, int length) {
    int sum = 0;
    for (int i = 0; i < length; i++) {
      sum += (value.charAt(i) - '0') * (length + 1 - i);
    }
    int remainder = (sum * 10) % 11;
    return remainder == 10 ? 0 : remainder;
  }
}
