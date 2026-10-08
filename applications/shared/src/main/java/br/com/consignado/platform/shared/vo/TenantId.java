package br.com.consignado.platform.shared.vo;

import java.util.regex.Pattern;

/**
 * Identificador do parceiro/correspondente. Allowlist de caracteres para evitar injecao em chaves,
 * logs e tags. Tratado como potencialmente pessoal (nao usar como tag de metrica sem a politica de
 * cardinalidade).
 */
public record TenantId(String value) {

  public static final int MAX_LENGTH = 32;
  private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9_-]{1," + MAX_LENGTH + "}$");

  public TenantId {
    if (value == null || !ALLOWED.matcher(value).matches()) {
      throw new IllegalArgumentException("X-Tenant-ID invalido");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
