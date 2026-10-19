package br.com.consignado.platform.legacy.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.consignado.platform.legacy.config.LegacyFaultPolicy;
import br.com.consignado.platform.legacy.ledger.AverbacaoLedger;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "legacy.api-key=test-key",
      "legacy.capacity-tps=1000",
      "legacy.fault-control.enabled=true",
      "legacy.fault-control.token=" + FaultControlTest.TOKEN
    })
@ActiveProfiles("sre-experiment")
@AutoConfigureMockMvc
class FaultControlTest {

  static final String TOKEN = "test-control-token-at-least-32-characters";
  private static final String PATH = "/internal/sre/fault";
  @Autowired MockMvc mvc;
  @Autowired LegacyFaultPolicy policy;
  @Autowired AverbacaoLedger ledger;
  @Autowired AtomicLong clock;

  @BeforeEach
  void restore() {
    policy.restore();
  }

  @Test
  void requires_a_separate_token_and_does_not_trust_forwarded_headers() throws Exception {
    mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    mvc.perform(get(PATH).header("X-SRE-Control-Token", "wrong"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            authorized(get(PATH))
                .header("X-Forwarded-For", "127.0.0.1")
                .with(
                    request -> {
                      request.setRemoteAddr("192.0.2.1");
                      return request;
                    }))
        .andExpect(status().isForbidden());
    mvc.perform(authorized(get(PATH)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void preserves_the_ledger_when_fault_is_restored_or_expires_without_the_client()
      throws Exception {
    String key = UUID.randomUUID().toString();
    long unique = ledger.uniqueCount();
    String first = call(key);
    mvc.perform(activate("{\"latencyMs\":0,\"errorRate\":1,\"durationMs\":100}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));
    mvc.perform(businessRequest(key)).andExpect(status().isServiceUnavailable());
    mvc.perform(activate("{\"latencyMs\":0,\"errorRate\":1,\"durationMs\":100}"))
        .andExpect(status().isConflict());
    clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(100));
    mvc.perform(businessRequest(key))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Replayed", "true"))
        .andExpect(
            result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(first));
    mvc.perform(activate("{\"latencyMs\":1,\"errorRate\":0,\"durationMs\":100}"))
        .andExpect(status().isOk());
    for (int i = 0; i < 2; i++) {
      mvc.perform(authorized(delete(PATH)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.active").value(false));
    }
    assertThat(call(key)).isEqualTo(first);
    assertThat(ledger.uniqueCount()).isEqualTo(unique + 1);
  }

  @Test
  void rejects_missing_fields_and_invalid_limits_without_arming() throws Exception {
    for (String body :
        new String[] {"{}", "{\"latencyMs\":0,\"errorRate\":1,\"durationMs\":300001}"}) {
      mvc.perform(activate(body)).andExpect(status().isBadRequest());
    }
    assertThat(policy.state().active()).isFalse();
  }

  private String call(String key) throws Exception {
    return mvc.perform(businessRequest(key))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static MockHttpServletRequestBuilder businessRequest(String key) {
    return post("/v1/averbacoes")
        .header("X-Api-Key", "test-key")
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content(AverbacaoControllerTest.BODY);
  }

  private static MockHttpServletRequestBuilder activate(String body) {
    return authorized(post(PATH)).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
    return request.header("X-SRE-Control-Token", TOKEN);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ClockConfiguration {
    @Bean
    AtomicLong testNanos() {
      return new AtomicLong();
    }

    @Bean
    @Primary
    LongSupplier testClock(AtomicLong testNanos) {
      return testNanos::get;
    }
  }
}
