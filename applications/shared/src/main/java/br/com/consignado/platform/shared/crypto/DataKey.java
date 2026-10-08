package br.com.consignado.platform.shared.crypto;

import java.util.Arrays;

/**
 * Chave de dados AES-256 para criptografia de envelope: o valor em claro (usado so em memoria) e a
 * forma cifrada, que acompanha o dado protegido para permitir a decifra posterior.
 */
public final class DataKey {

  private final byte[] plaintext;
  private final byte[] encrypted;

  public DataKey(byte[] plaintext, byte[] encrypted) {
    if (plaintext == null || plaintext.length != 32 || encrypted == null) {
      throw new IllegalArgumentException("Chave de dados invalida");
    }
    this.plaintext = plaintext.clone();
    this.encrypted = encrypted.clone();
  }

  public byte[] plaintext() {
    return plaintext.clone();
  }

  public byte[] encrypted() {
    return encrypted.clone();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof DataKey that
        && Arrays.equals(plaintext, that.plaintext)
        && Arrays.equals(encrypted, that.encrypted);
  }

  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(plaintext) + Arrays.hashCode(encrypted);
  }

  /** Nunca expoe material de chave. */
  @Override
  public String toString() {
    return "DataKey[***]";
  }
}
