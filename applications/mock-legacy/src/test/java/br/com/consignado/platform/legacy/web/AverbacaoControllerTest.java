package br.com.consignado.platform.legacy.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {"legacy.api-key=test-key", "legacy.capacity-tps=1000"})
@AutoConfigureMockMvc
class AverbacaoControllerTest {

  static final String BODY =
      "{\"cpf\":\"52998224725\",\"convenio\":\"INSS\",\"amount\":15000.50,\"installments\":84}";

  @Autowired MockMvc mvc;
  @Autowired MeterRegistry registry;

  @Test
  void fault_control_is_absent_by_default() throws Exception {
    mvc.perform(get("/internal/sre/fault")).andExpect(status().isNotFound());
  }

  private ResultActions call(String apiKey, String idempotencyKey, String body) throws Exception {
    var request = post("/v1/averbacoes").contentType(MediaType.APPLICATION_JSON).content(body);
    if (apiKey != null) {
      request.header("X-Api-Key", apiKey);
    }
    if (idempotencyKey != null) {
      request.header("Idempotency-Key", idempotencyKey);
    }
    return mvc.perform(request);
  }

  private double count(String name, String tag, String value) {
    return registry.get(name).tag(tag, value).counter().count();
  }

  @Test
  void should_reject_missing_or_wrong_api_key() throws Exception {
    call(null, UUID.randomUUID().toString(), BODY).andExpect(status().isUnauthorized());
    call("outra", UUID.randomUUID().toString(), BODY).andExpect(status().isUnauthorized());
  }

  @Test
  void should_create_an_averbacao_for_a_valid_request() throws Exception {
    double before = count("legacy.averbacoes", "kind", "unique");

    call("test-key", UUID.randomUUID().toString(), BODY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AVERBADA"))
        .andExpect(jsonPath("$.averbacaoId").isNotEmpty())
        .andExpect(header().doesNotExist("X-Replayed"));

    assertThat(count("legacy.averbacoes", "kind", "unique")).isEqualTo(before + 1);
  }

  @Test
  void should_replay_the_same_result_for_a_repeated_idempotency_key() throws Exception {
    String key = UUID.randomUUID().toString();
    double uniqueBefore = count("legacy.averbacoes", "kind", "unique");
    double duplicateBefore = count("legacy.averbacoes", "kind", "duplicate");

    String first =
        call("test-key", key, BODY)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String second =
        call("test-key", key, BODY)
            .andExpect(status().isOk())
            .andExpect(header().string("X-Replayed", "true"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(second).isEqualTo(first);
    assertThat(count("legacy.averbacoes", "kind", "unique")).isEqualTo(uniqueBefore + 1);
    assertThat(count("legacy.averbacoes", "kind", "duplicate")).isEqualTo(duplicateBefore + 1);
  }

  @Test
  void should_return_400_for_invalid_idempotency_key_and_missing_header() throws Exception {
    call("test-key", "nao-e-uuid", BODY).andExpect(status().isBadRequest());
    call("test-key", null, BODY).andExpect(status().isBadRequest());
  }

  @Test
  void should_return_400_for_invalid_body_without_echoing_the_cpf() throws Exception {
    String invalid = "{\"cpf\":\"abc\",\"convenio\":\"INSS\",\"amount\":1,\"installments\":12}";

    String response =
        call("test-key", UUID.randomUUID().toString(), invalid)
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(response).doesNotContain("abc");
  }
}
