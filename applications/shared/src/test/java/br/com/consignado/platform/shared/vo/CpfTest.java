package br.com.consignado.platform.shared.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CpfTest {

  private static final String VALID = "52998224725";

  @Test
  void should_accept_valid_cpf_with_and_without_punctuation() {
    assertThat(Cpf.of("529.982.247-25").digits()).isEqualTo(VALID);
    assertThat(Cpf.of(VALID).digits()).isEqualTo(VALID);
  }

  @Test
  void should_mask_cpf_in_to_string_when_logged() {
    String text = new Cpf(VALID).toString();

    assertThat(text).isEqualTo("***.***.***-25").doesNotContain("529982247");
  }

  @Test
  void should_reject_cpf_with_wrong_check_digits() {
    assertThatThrownBy(() -> new Cpf("52998224726")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_cpf_with_all_same_digits() {
    assertThatThrownBy(() -> new Cpf("11111111111")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_cpf_with_unexpected_characters() {
    assertThatThrownBy(() -> Cpf.of("529 982 247 25")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Cpf.of("529982247-2a")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_null_and_wrong_length() {
    assertThatThrownBy(() -> Cpf.of(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Cpf(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Cpf("123")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_not_expose_value_in_error_message() {
    assertThatThrownBy(() -> new Cpf("52998224726")).hasMessageNotContaining("5299822472");
  }
}
