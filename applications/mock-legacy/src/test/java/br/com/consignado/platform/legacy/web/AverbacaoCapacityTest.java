package br.com.consignado.platform.legacy.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.consignado.platform.legacy.capacity.CapacityGate;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Relogio congelado: a janela de 1 s nunca expira, o que torna o teste deterministico. */
@SpringBootTest(properties = {"legacy.api-key=test-key", "legacy.capacity-tps=2"})
@AutoConfigureMockMvc
@Import(AverbacaoCapacityTest.FrozenClock.class)
class AverbacaoCapacityTest {

  @TestConfiguration
  static class FrozenClock {
    @Bean
    @Primary
    LongSupplier frozenClock() {
      return () -> 42_000_000_000L;
    }
  }

  @Autowired MockMvc mvc;
  @Autowired CapacityGate gate;

  private ResultActions call() throws Exception {
    return mvc.perform(
        post("/v1/averbacoes")
            .header("X-Api-Key", "test-key")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(AverbacaoControllerTest.BODY));
  }

  @Test
  void should_answer_503_with_retry_after_when_the_one_second_budget_is_exhausted()
      throws Exception {
    call().andExpect(status().isOk());
    call().andExpect(status().isOk());
    call()
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "1"));
    call().andExpect(status().isServiceUnavailable());

    assertThat(gate.peak()).isEqualTo(2);
  }
}
