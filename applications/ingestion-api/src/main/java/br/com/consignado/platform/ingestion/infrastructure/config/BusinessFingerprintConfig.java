package br.com.consignado.platform.ingestion.infrastructure.config;

import br.com.consignado.platform.ingestion.application.port.out.BusinessFingerprintPort;
import br.com.consignado.platform.ingestion.application.service.BusinessFingerprintService;
import br.com.consignado.platform.ingestion.domain.InboxUnavailableException;
import java.time.Duration;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GenerateMacRequest;
import software.amazon.awssdk.services.kms.model.MacAlgorithmSpec;

/** Explicit opt-in. KMS HMAC secrets stay in KMS; local mode requires an explicit test key. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ingestion.fingerprint.enabled", havingValue = "true")
public class BusinessFingerprintConfig {
  @Bean(name = "fingerprintKmsClient", destroyMethod = "close")
  @ConditionalOnProperty(
      name = "ingestion.fingerprint.mode",
      havingValue = "kms",
      matchIfMissing = true)
  KmsClient fingerprintKmsClient() {
    return KmsClient.builder()
        .overrideConfiguration(
            ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(2))
                .apiCallAttemptTimeout(Duration.ofSeconds(1))
                .build())
        .build();
  }

  @Bean
  @ConditionalOnProperty(
      name = "ingestion.fingerprint.mode",
      havingValue = "kms",
      matchIfMissing = true)
  BusinessFingerprintPort kmsFingerprint(
      @Qualifier("fingerprintKmsClient") KmsClient kms, Environment environment) {
    String keyId = environment.getRequiredProperty("ingestion.fingerprint.kms-key-id");
    if (keyId.isBlank() || keyId.startsWith("alias/") || keyId.contains(":alias/")) {
      throw new IllegalArgumentException("Use an immutable KMS HMAC key ID/ARN, not an alias");
    }
    String version = environment.getRequiredProperty("ingestion.fingerprint.key-version");
    return new BusinessFingerprintService(
        data -> {
          try {
            return kms.generateMac(
                    GenerateMacRequest.builder()
                        .keyId(keyId)
                        .macAlgorithm(MacAlgorithmSpec.HMAC_SHA_256)
                        .message(SdkBytes.fromByteArray(data))
                        .build())
                .mac()
                .asByteArray();
          } catch (SdkException e) {
            throw new InboxUnavailableException("Fingerprint HMAC unavailable", e);
          }
        },
        version);
  }

  @Bean
  @ConditionalOnProperty(name = "ingestion.fingerprint.mode", havingValue = "local")
  BusinessFingerprintPort localFingerprint(Environment environment) {
    if (!"memory".equals(environment.getProperty("ingestion.inbox.type"))) {
      throw new IllegalArgumentException(
          "Local HMAC keys are restricted to the in-memory development inbox");
    }
    return BusinessFingerprintService.local(
        Base64.getDecoder()
            .decode(environment.getRequiredProperty("ingestion.fingerprint.local-key")),
        environment.getRequiredProperty("ingestion.fingerprint.key-version"));
  }

  @Bean
  Object validateFingerprintMode(Environment environment) {
    String mode = environment.getProperty("ingestion.fingerprint.mode", "kms");
    if (!mode.equals("kms") && !mode.equals("local")) {
      throw new IllegalArgumentException("Fingerprint mode must be kms or local");
    }
    return new Object();
  }
}
