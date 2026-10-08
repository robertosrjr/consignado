package br.com.consignado.platform.aws;

import br.com.consignado.platform.shared.crypto.DataKey;
import br.com.consignado.platform.shared.crypto.DataKeyProvider;
import br.com.consignado.platform.shared.crypto.PayloadProtectionException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DataKeySpec;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;

/**
 * Provedor de chaves de dados via AWS KMS (criptografia de envelope). Reutiliza uma chave de dados
 * por um numero limitado de usos e por tempo limitado, o que reduz as chamadas ao KMS (cobradas por
 * requisicao) de uma por proposta para uma por lote. Chaves decifradas ficam num cache LRU pequeno.
 */
public final class KmsDataKeyProvider implements DataKeyProvider {

  private static final int DECRYPTED_CACHE_SIZE = 64;

  private final KmsClient kms;
  private final String keyId;
  private final int maxUses;
  private final Duration maxAge;
  private final Clock clock;

  private DataKey cached;
  private int uses;
  private Instant createdAt = Instant.EPOCH;

  private final Map<String, byte[]> decrypted =
      new LinkedHashMap<>(16, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
          return size() > DECRYPTED_CACHE_SIZE;
        }
      };

  public KmsDataKeyProvider(
      KmsClient kms, String keyId, int maxUses, Duration maxAge, Clock clock) {
    if (keyId == null || keyId.isBlank() || maxUses < 1 || maxAge.isNegative()) {
      throw new IllegalArgumentException("Configuracao de KMS invalida");
    }
    this.kms = kms;
    this.keyId = keyId;
    this.maxUses = maxUses;
    this.maxAge = maxAge;
    this.clock = clock;
  }

  @Override
  public synchronized DataKey currentKey() {
    Instant now = clock.instant();
    if (cached == null || uses >= maxUses || now.isAfter(createdAt.plus(maxAge))) {
      cached = generate();
      uses = 0;
      createdAt = now;
    }
    uses++;
    return cached;
  }

  @Override
  public synchronized byte[] decryptKey(byte[] encryptedKey) {
    String cacheKey = Base64.getEncoder().encodeToString(encryptedKey);
    byte[] hit = decrypted.get(cacheKey);
    if (hit != null) {
      return hit.clone();
    }
    try {
      byte[] plaintext =
          kms.decrypt(
                  DecryptRequest.builder()
                      .keyId(keyId)
                      .ciphertextBlob(SdkBytes.fromByteArray(encryptedKey))
                      .build())
              .plaintext()
              .asByteArray();
      decrypted.put(cacheKey, plaintext.clone());
      return plaintext;
    } catch (SdkException e) {
      throw new PayloadProtectionException("Falha ao decifrar chave de dados", e);
    }
  }

  private DataKey generate() {
    try {
      GenerateDataKeyResponse response =
          kms.generateDataKey(
              GenerateDataKeyRequest.builder().keyId(keyId).keySpec(DataKeySpec.AES_256).build());
      return new DataKey(
          response.plaintext().asByteArray(), response.ciphertextBlob().asByteArray());
    } catch (SdkException e) {
      throw new PayloadProtectionException("Falha ao gerar chave de dados", e);
    }
  }
}
