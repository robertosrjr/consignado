package br.com.consignado.platform.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.shared.crypto.DataKey;
import br.com.consignado.platform.shared.crypto.PayloadEnvelope;
import br.com.consignado.platform.shared.crypto.PayloadProtectionException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class KmsDataKeyProviderTest {

  /** Relogio controlavel. */
  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-08T00:00:00Z");

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private final FakeKmsClient kms = new FakeKmsClient();
  private final MutableClock clock = new MutableClock();

  private KmsDataKeyProvider provider(int maxUses, Duration maxAge) {
    return new KmsDataKeyProvider(kms, "alias/consignado-poc-payload", maxUses, maxAge, clock);
  }

  @Test
  void should_reuse_the_data_key_until_the_usage_limit_is_reached() {
    KmsDataKeyProvider provider = provider(3, Duration.ofMinutes(5));

    DataKey first = provider.currentKey();
    provider.currentKey();
    provider.currentKey();
    assertThat(kms.generateCalls).isEqualTo(1);

    DataKey rotated = provider.currentKey();
    assertThat(kms.generateCalls).isEqualTo(2);
    assertThat(rotated).isNotEqualTo(first);
  }

  @Test
  void should_rotate_the_data_key_after_the_maximum_age() {
    KmsDataKeyProvider provider = provider(1000, Duration.ofMinutes(5));
    provider.currentKey();

    clock.advance(Duration.ofMinutes(6));
    provider.currentKey();

    assertThat(kms.generateCalls).isEqualTo(2);
  }

  @Test
  void should_cache_decrypted_keys_to_avoid_repeated_kms_calls() {
    KmsDataKeyProvider provider = provider(1000, Duration.ofMinutes(5));
    DataKey key = provider.currentKey();

    byte[] first = provider.decryptKey(key.encrypted());
    byte[] second = provider.decryptKey(key.encrypted());

    assertThat(first).isEqualTo(key.plaintext()).isEqualTo(second);
    assertThat(kms.decryptCalls).isEqualTo(1);
  }

  @Test
  void should_round_trip_a_payload_through_the_envelope() {
    PayloadEnvelope envelope = new PayloadEnvelope(provider(1000, Duration.ofMinutes(5)));
    byte[] plaintext = "52998224725".getBytes(StandardCharsets.UTF_8);

    assertThat(envelope.unprotect(envelope.protect(plaintext))).isEqualTo(plaintext);
    assertThat(kms.generateCalls).isEqualTo(1);
  }

  @Test
  void should_translate_kms_failures_without_leaking_details() {
    KmsDataKeyProvider provider = provider(10, Duration.ofMinutes(5));
    kms.failing = true;

    assertThatThrownBy(provider::currentKey)
        .isInstanceOf(PayloadProtectionException.class)
        .hasMessageNotContaining("kms indisponivel");
    assertThatThrownBy(() -> provider.decryptKey(new byte[] {1, 2, 3}))
        .isInstanceOf(PayloadProtectionException.class);
  }

  @Test
  void should_reject_invalid_configuration() {
    assertThatThrownBy(() -> new KmsDataKeyProvider(kms, " ", 1, Duration.ofSeconds(1), clock))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new KmsDataKeyProvider(kms, "k", 0, Duration.ofSeconds(1), clock))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new KmsDataKeyProvider(kms, "k", 1, Duration.ofSeconds(-1), clock))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
