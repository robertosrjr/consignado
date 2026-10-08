package br.com.consignado.platform.shared.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class LocalDataKeyProviderTest {

  @Test
  void should_build_from_base64_key() {
    String base64 = Base64.getEncoder().encodeToString(new byte[32]);

    LocalDataKeyProvider provider = LocalDataKeyProvider.fromBase64(base64);

    assertThat(provider.currentKey().plaintext()).hasSize(32);
    assertThat(provider.decryptKey(new byte[0])).hasSize(32);
  }

  @Test
  void should_reject_missing_or_invalid_keys() {
    assertThatThrownBy(() -> LocalDataKeyProvider.fromBase64(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LocalDataKeyProvider.fromBase64(" "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LocalDataKeyProvider.fromBase64("***"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> LocalDataKeyProvider.fromBase64(Base64.getEncoder().encodeToString(new byte[16])))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_refuse_to_decrypt_a_key_it_did_not_produce() {
    LocalDataKeyProvider provider = new LocalDataKeyProvider(new byte[32]);

    assertThatThrownBy(() -> provider.decryptKey(new byte[] {1, 2, 3}))
        .isInstanceOf(PayloadProtectionException.class);
  }

  @Test
  void should_never_print_key_material() {
    DataKey key = new DataKey(new byte[32], new byte[] {9});

    assertThat(key.toString()).isEqualTo("DataKey[***]");
    assertThat(key)
        .isEqualTo(new DataKey(new byte[32], new byte[] {9}))
        .hasSameHashCodeAs(new DataKey(new byte[32], new byte[] {9}));
    assertThatThrownBy(() -> new DataKey(new byte[5], new byte[0]))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
