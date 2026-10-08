package br.com.consignado.platform.aws;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;

/** KMS falso e deterministico para testes (sem rede, sem emulador). */
final class FakeKmsClient implements KmsClient {

  int generateCalls;
  int decryptCalls;
  boolean failing;

  private final SecureRandom random = new SecureRandom();
  private final Map<String, byte[]> keys = new HashMap<>();

  @Override
  public GenerateDataKeyResponse generateDataKey(GenerateDataKeyRequest request) {
    generateCalls++;
    if (failing) {
      throw SdkClientException.create("kms indisponivel");
    }
    byte[] plaintext = new byte[32];
    random.nextBytes(plaintext);
    byte[] blob = new byte[16];
    random.nextBytes(blob);
    keys.put(java.util.Base64.getEncoder().encodeToString(blob), plaintext);
    return GenerateDataKeyResponse.builder()
        .plaintext(SdkBytes.fromByteArray(plaintext))
        .ciphertextBlob(SdkBytes.fromByteArray(blob))
        .build();
  }

  @Override
  public DecryptResponse decrypt(DecryptRequest request) {
    decryptCalls++;
    if (failing) {
      throw SdkClientException.create("kms indisponivel");
    }
    byte[] plaintext =
        keys.get(
            java.util.Base64.getEncoder().encodeToString(request.ciphertextBlob().asByteArray()));
    if (plaintext == null) {
      throw SdkClientException.create("chave desconhecida");
    }
    return DecryptResponse.builder().plaintext(SdkBytes.fromByteArray(plaintext)).build();
  }

  @Override
  public String serviceName() {
    return "kms";
  }

  @Override
  public void close() {}
}
