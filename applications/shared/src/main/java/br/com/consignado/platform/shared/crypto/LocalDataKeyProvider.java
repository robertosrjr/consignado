package br.com.consignado.platform.shared.crypto;

import java.util.Base64;

/**
 * Chave AES-256 fornecida por configuracao (variavel de ambiente), para desenvolvimento e testes.
 * Nunca deve ser usada com chave versionada; em producao usa-se KMS.
 */
public final class LocalDataKeyProvider implements DataKeyProvider {

  private static final byte[] NO_ENCRYPTED_KEY = new byte[0];

  private final DataKey key;

  public LocalDataKeyProvider(byte[] aes256Key) {
    this.key = new DataKey(aes256Key, NO_ENCRYPTED_KEY);
  }

  /** Cria a partir de uma chave de 32 bytes em Base64. */
  public static LocalDataKeyProvider fromBase64(String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      throw new IllegalArgumentException("Chave local ausente");
    }
    try {
      return new LocalDataKeyProvider(Base64.getDecoder().decode(base64Key.trim()));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Chave local invalida", e);
    }
  }

  @Override
  public DataKey currentKey() {
    return key;
  }

  @Override
  public byte[] decryptKey(byte[] encryptedKey) {
    if (encryptedKey.length != 0) {
      throw new PayloadProtectionException("Envelope nao pertence a esta chave", null);
    }
    return key.plaintext();
  }
}
