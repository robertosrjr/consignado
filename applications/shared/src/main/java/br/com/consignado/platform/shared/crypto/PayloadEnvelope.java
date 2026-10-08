package br.com.consignado.platform.shared.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Criptografia de envelope: comprime (GZIP) e cifra (AES-256-GCM) o payload com uma chave de dados
 * obtida de um {@link DataKeyProvider}. Formato textual:
 *
 * <pre>v1.&lt;chave cifrada&gt;.&lt;iv&gt;.&lt;texto cifrado + tag&gt;</pre>
 *
 * (campos em Base64 URL-safe). O cabecalho e autenticado como AAD, entao adulteracao e detectada.
 * Limites de tamanho protegem contra envelopes e descompressoes abusivos.
 */
public final class PayloadEnvelope {

  static final int MAX_ENVELOPE_CHARS = 64 * 1024;
  static final int MAX_PLAINTEXT_BYTES = 1024 * 1024;

  private static final String VERSION = "v1";
  private static final byte[] AAD = "consignado-payload-v1".getBytes(StandardCharsets.UTF_8);
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  private final DataKeyProvider provider;
  private final SecureRandom random = new SecureRandom();

  public PayloadEnvelope(DataKeyProvider provider) {
    this.provider = provider;
  }

  public String protect(byte[] plaintext) {
    if (plaintext == null || plaintext.length > MAX_PLAINTEXT_BYTES) {
      throw new PayloadProtectionException("Payload invalido", null);
    }
    try {
      DataKey dataKey = provider.currentKey();
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(dataKey.plaintext(), "AES"),
          new GCMParameterSpec(TAG_BITS, iv));
      cipher.updateAAD(AAD);
      byte[] encrypted = cipher.doFinal(gzip(plaintext));
      return VERSION
          + "."
          + ENCODER.encodeToString(dataKey.encrypted())
          + "."
          + ENCODER.encodeToString(iv)
          + "."
          + ENCODER.encodeToString(encrypted);
    } catch (GeneralSecurityException | IOException e) {
      throw new PayloadProtectionException("Falha ao proteger payload", e);
    }
  }

  public byte[] unprotect(String envelope) {
    if (envelope == null || envelope.length() > MAX_ENVELOPE_CHARS) {
      throw new PayloadProtectionException("Envelope invalido", null);
    }
    String[] parts = envelope.split("\\.", -1);
    if (parts.length != 4 || !VERSION.equals(parts[0])) {
      throw new PayloadProtectionException("Envelope invalido", null);
    }
    try {
      byte[] encryptedKey = DECODER.decode(parts[1]);
      byte[] iv = DECODER.decode(parts[2]);
      byte[] encrypted = DECODER.decode(parts[3]);
      if (iv.length != IV_BYTES) {
        throw new PayloadProtectionException("Envelope invalido", null);
      }
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(provider.decryptKey(encryptedKey), "AES"),
          new GCMParameterSpec(TAG_BITS, iv));
      cipher.updateAAD(AAD);
      return gunzip(cipher.doFinal(encrypted));
    } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
      throw new PayloadProtectionException("Falha ao recuperar payload", e);
    }
  }

  private static byte[] gzip(byte[] data) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 2 + 32);
    try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
      gzip.write(data);
    }
    return out.toByteArray();
  }

  private static byte[] gunzip(byte[] data) throws IOException {
    try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
      byte[] bytes = in.readNBytes(MAX_PLAINTEXT_BYTES + 1);
      if (bytes.length > MAX_PLAINTEXT_BYTES) {
        throw new IOException("Payload descomprimido excede o limite");
      }
      return bytes;
    }
  }
}
