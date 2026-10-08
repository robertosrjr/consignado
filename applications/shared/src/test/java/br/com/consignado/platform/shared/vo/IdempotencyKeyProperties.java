package br.com.consignado.platform.shared.vo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.testkit.DomainArbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Round-trip de {@link IdempotencyKey} (PBT-02). */
class IdempotencyKeyProperties {

  @Provide
  Arbitrary<IdempotencyKey> keys() {
    return DomainArbitraries.idempotencyKey();
  }

  @Property
  void parse_of_to_string_is_identity(@ForAll("keys") IdempotencyKey key) {
    assertThat(IdempotencyKey.parse(key.toString())).isEqualTo(key);
  }

  @Property
  void to_string_is_always_36_lowercase_characters(@ForAll("keys") IdempotencyKey key) {
    String text = key.toString();

    assertThat(text).hasSize(36).isEqualTo(text.toLowerCase());
  }
}
