package br.com.consignado.platform.shared.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TenantIdTest {

  @Test
  void should_accept_allowed_characters_up_to_max_length() {
    assertThat(new TenantId("Banco_Parceiro-01").value()).isEqualTo("Banco_Parceiro-01");
    assertThat(new TenantId("a".repeat(TenantId.MAX_LENGTH)).value()).hasSize(32);
  }

  @Test
  void should_reject_empty_too_long_and_special_characters() {
    assertThatThrownBy(() -> new TenantId("")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TenantId("a".repeat(33)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TenantId("tenant one"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TenantId("tenant;drop"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TenantId("tenant\nINJECT"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TenantId("parceiro-á"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TenantId(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
