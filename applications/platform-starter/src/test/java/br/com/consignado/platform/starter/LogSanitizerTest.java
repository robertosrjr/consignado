package br.com.consignado.platform.starter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LogSanitizerTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "123.456.789-10                | ***.***.789-10",
        "52998224725                   | ***.***.247-25",
        "joana.silva@empresa.com.br    | j***a@empresa.com.br",
        "4111 1111 1111 1111           | 4111-XXXX-XXXX-1111",
        "(11) 98765-4321               | (**) *****-4321"
      })
  void should_mask_each_documented_format(String raw, String expected) {
    assertThat(LogSanitizer.sanitize(raw.trim())).isEqualTo(expected.trim());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "SELECT * FROM proposal WHERE cpf = 52998224725",
        "falha ao averbar cpf=529.982.247-25",
        "{\"customerCpf\":\"52998224725\"}"
      })
  void should_never_leave_a_full_cpf_in_the_text(String raw) {
    String masked = LogSanitizer.sanitize(raw);

    assertThat(masked).doesNotContain("52998224725").doesNotContain("529.982.247-25");
    assertThat(masked).contains("247-25"); // mantem o valor diagnostico (ultimo grupo)
  }

  @Test
  void should_mask_every_occurrence_in_the_same_text() {
    String masked = LogSanitizer.sanitize("cpf1=52998224725 cpf2=11144477735");

    assertThat(masked).isEqualTo("cpf1=***.***.247-25 cpf2=***.***.777-35");
  }

  @Test
  void should_keep_technical_text_untouched() {
    String technical = "event=inbox_unavailable tenant=banco-a lane=FAST attempts=2 status=503";

    assertThat(LogSanitizer.sanitize(technical)).isEqualTo(technical);
  }

  @Test
  void should_return_an_empty_string_for_null() {
    assertThat(LogSanitizer.sanitize(null)).isEmpty();
  }
}
