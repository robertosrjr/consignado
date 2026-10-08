package br.com.consignado.platform.legacy.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "legacy.api-key=test-key",
      "legacy.capacity-tps=1000",
      "legacy.chaos.error-rate=1.0"
    })
@AutoConfigureMockMvc
class AverbacaoChaosTest {

  @Autowired MockMvc mvc;

  @Test
  void should_fail_with_503_when_the_simulated_error_rate_is_100_percent() throws Exception {
    mvc.perform(
            post("/v1/averbacoes")
                .header("X-Api-Key", "test-key")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(AverbacaoControllerTest.BODY))
        .andExpect(status().isServiceUnavailable());
  }
}
