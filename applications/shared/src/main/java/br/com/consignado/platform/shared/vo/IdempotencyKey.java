package br.com.consignado.platform.shared.vo;

import java.util.UUID;
import java.util.regex.Pattern;

/** Chave de idempotencia gerada pelo parceiro: UUID em formato canonico (36 caracteres). */
public record IdempotencyKey(UUID value) {

  private static final Pattern CANONICAL =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

  public IdempotencyKey {
    if (value == null) {
      throw new IllegalArgumentException("Idempotency-Key invalida");
    }
  }

  /**
   * Parse estrito: rejeita formatos que {@link UUID#fromString(String)} aceitaria de forma frouxa.
   */
  public static IdempotencyKey parse(String raw) {
    if (raw == null || !CANONICAL.matcher(raw).matches()) {
      throw new IllegalArgumentException("Idempotency-Key invalida");
    }
    return new IdempotencyKey(UUID.fromString(raw));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
