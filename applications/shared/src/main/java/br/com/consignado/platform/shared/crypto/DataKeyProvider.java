package br.com.consignado.platform.shared.crypto;

/**
 * Origem das chaves de dados da criptografia de envelope. Implementacoes: chave local (JDK, para
 * desenvolvimento e testes) e KMS (modulo platform-aws).
 */
public interface DataKeyProvider {

  /** Chave para cifrar novos dados (pode ser reutilizada por um numero limitado de usos). */
  DataKey currentKey();

  /** Recupera a chave de dados em claro a partir da forma cifrada gravada no envelope. */
  byte[] decryptKey(byte[] encryptedKey);
}
