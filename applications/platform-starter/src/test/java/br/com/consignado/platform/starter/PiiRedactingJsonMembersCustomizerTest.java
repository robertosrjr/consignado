package br.com.consignado.platform.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonWriter;

class PiiRedactingJsonMembersCustomizerTest {

  private final PiiRedactingJsonMembersCustomizer<Map<String, Object>> customizer =
      new PiiRedactingJsonMembersCustomizer<>();

  private String writeLogLike(Map<String, Object> event) {
    JsonWriter<Map<String, Object>> writer =
        JsonWriter.of(
            members -> {
              members.add("message", (Map<String, Object> e) -> e.get("message"));
              members.add("error.stack_trace", (Map<String, Object> e) -> e.get("stack"));
              members.add("tenant", (Map<String, Object> e) -> e.get("tenant"));
              customizer.customize(members);
            });
    return writer.writeToString(event);
  }

  @Test
  void should_mask_pii_in_the_message_and_in_the_stack_trace() {
    String json =
        writeLogLike(
            Map.of(
                "message",
                "falha para cpf 52998224725",
                "stack",
                "java.lang.IllegalStateException: SELECT * FROM proposal WHERE cpf = 52998224725",
                "tenant",
                "banco-a"));

    assertThat(json).doesNotContain("52998224725");
    assertThat(json).contains("***.***.247-25");
    assertThat(json).contains("banco-a"); // campo tecnico preservado
  }

  @Test
  void should_mask_email_card_and_phone_anywhere_in_the_event() {
    String json =
        writeLogLike(
            Map.of(
                "message",
                "contato joana.silva@empresa.com.br cartao 4111 1111 1111 1111",
                "stack",
                "telefone (11) 98765-4321",
                "tenant",
                "banco-a"));

    assertThat(json)
        .doesNotContain("joana.silva@empresa.com.br")
        .doesNotContain("4111 1111 1111 1111")
        .doesNotContain("98765-4321");
    assertThat(json).contains("j***a@empresa.com.br").contains("4111-XXXX-XXXX-1111");
  }

  @Test
  void should_be_registered_in_the_platform_defaults() {
    assertThat(PlatformDefaultsEnvironmentPostProcessor.defaults())
        .containsEntry(
            "logging.structured.json.customizer",
            PiiRedactingJsonMembersCustomizer.class.getName());
  }
}
