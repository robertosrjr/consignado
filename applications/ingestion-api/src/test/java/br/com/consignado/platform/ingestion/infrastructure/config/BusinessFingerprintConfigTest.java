package br.com.consignado.platform.ingestion.infrastructure.config;

import static org.assertj.core.api.Assertions.*;

import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalCommand;
import br.com.consignado.platform.ingestion.domain.InboxUnavailableException;
import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.vo.*;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.*;

class BusinessFingerprintConfigTest {
  private final BusinessFingerprintConfig config = new BusinessFingerprintConfig();

  private static final class Kms implements KmsClient {
    GenerateMacRequest last;
    RuntimeException failure;

    @Override
    public String serviceName() {
      return "kms";
    }

    @Override
    public void close() {}

    @Override
    public GenerateMacResponse generateMac(GenerateMacRequest request) {
      last = request;
      if (failure != null) throw failure;
      return GenerateMacResponse.builder().mac(SdkBytes.fromByteArray(new byte[32])).build();
    }
  }

  @Test
  void sends_hmac_to_the_configured_kms_key_and_fails_closed_on_kms_outage() {
    var env =
        new MockEnvironment()
            .withProperty("ingestion.fingerprint.kms-key-id", "key-uuid")
            .withProperty("ingestion.fingerprint.key-version", "v2");
    Kms kms = new Kms();
    var signer = config.kmsFingerprint(kms, env);
    var command =
        new AcceptProposalCommand(
            new IdempotencyKey(UUID.randomUUID()),
            new TenantId("a"),
            Cpf.of("52998224725"),
            ConvenioType.INSS,
            false,
            Money.of("1"),
            1);
    signer.generate(command, LocalDate.of(2026, 10, 16));
    assertThat(kms.last.keyId()).isEqualTo("key-uuid");
    assertThat(kms.last.macAlgorithm()).isEqualTo(MacAlgorithmSpec.HMAC_SHA_256);
    kms.failure = SdkClientException.create("request data must not leak");
    assertThatThrownBy(() -> signer.generate(command, LocalDate.of(2026, 10, 16)))
        .isInstanceOf(InboxUnavailableException.class)
        .hasMessageNotContaining("request data");
  }

  @Test
  void rejects_missing_or_mutable_keys_and_unknown_modes() {
    var env =
        new MockEnvironment().withProperty("ingestion.fingerprint.kms-key-id", "alias/current");
    assertThatThrownBy(() -> config.kmsFingerprint(new Kms(), env))
        .isInstanceOf(IllegalArgumentException.class);
    env.withProperty("ingestion.fingerprint.kms-key-id", "arn:aws:kms:sa-east-1:123:alias/current");
    assertThatThrownBy(() -> config.kmsFingerprint(new Kms(), env))
        .isInstanceOf(IllegalArgumentException.class);
    env.withProperty("ingestion.fingerprint.kms-key-id", "");
    assertThatThrownBy(() -> config.kmsFingerprint(new Kms(), env))
        .isInstanceOf(IllegalArgumentException.class);
    env.withProperty("ingestion.fingerprint.mode", "unrecognized");
    assertThatThrownBy(() -> config.validateFingerprintMode(env))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(config.validateFingerprintMode(new MockEnvironment())).isNotNull();
  }

  @Test
  void local_mode_requires_memory_and_an_explicit_long_secret() {
    var env = new MockEnvironment().withProperty("ingestion.inbox.type", "dynamodb");
    assertThatThrownBy(() -> config.localFingerprint(env))
        .isInstanceOf(IllegalArgumentException.class);
    env.withProperty("ingestion.inbox.type", "memory");
    assertThatThrownBy(() -> config.localFingerprint(env))
        .isInstanceOf(IllegalStateException.class);
    env.withProperty("ingestion.fingerprint.local-key", "AA==")
        .withProperty("ingestion.fingerprint.key-version", "v2");
    assertThatThrownBy(() -> config.localFingerprint(env))
        .isInstanceOf(IllegalArgumentException.class);
    env.withProperty(
        "ingestion.fingerprint.local-key", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    assertThat(config.localFingerprint(env)).isNotNull();
  }
}
