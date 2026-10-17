package br.com.consignado.platform.ingestion.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(
    properties = {
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false",
      "ingestion.inbox.type=memory",
      "ingestion.fingerprint.enabled=true",
      "ingestion.fingerprint.mode=local",
      "ingestion.fingerprint.local-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "ingestion.payload.local-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "ingestion.jwt.jwk-set-uri=http://localhost:9/jwks",
      "ingestion.quota.default-per-second=10000"
    })
@AutoConfigureMockMvc
class BusinessFingerprintControllerTest {
  @Autowired MockMvc mvc;
  @Autowired io.micrometer.core.instrument.MeterRegistry registry;

  private ResultActions post(String key, String tenant, String amount, String date)
      throws Exception {
    String body =
        "{\"customerCpf\":\"52998224725\",\"convenioCode\":\" inss  \",\"isPortability\":false,"
            + "\"amount\":"
            + amount
            + ",\"installments\":84,\"contractDate\":\""
            + date
            + "\"}";
    return mvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/proposals")
            .header("Idempotency-Key", key)
            .header("X-Tenant-ID", tenant)
            .with(jwt().jwt(j -> j.claim("tenant", tenant)))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Test
  void distinguishes_technical_and_semantic_retries_and_preserves_original_proposal()
      throws Exception {
    var vgv =
        registry
            .get("business.vgv.received")
            .tags("convenio", "inss", "portability", "false")
            .summary();
    long countBefore = vgv.count();
    String tenant = "test-" + UUID.randomUUID().toString().substring(0, 8);
    String key = UUID.randomUUID().toString();
    String first =
        post(key, tenant, "15000", "2026-10-16")
            .andExpect(status().isAccepted())
            .andExpect(header().string("X-Idempotency-Status", "NEW"))
            .andExpect(jsonPath("$.proposalId").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String technical =
        post(key, tenant, "15000.00", "2026-10-16")
            .andExpect(header().string("X-Idempotency-Status", "DUPLICATE_ACCEPTED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String newKey = UUID.randomUUID().toString();
    String semantic =
        post(newKey, tenant, "15000.0", "2026-10-16")
            .andExpect(status().isAccepted())
            .andExpect(header().string("X-Idempotency-Status", "DUPLICATE_REJECTED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var mapper = new tools.jackson.databind.ObjectMapper();
    String id = mapper.readTree(first).get("proposalId").asString();
    assertThat(id).isNotEqualTo(key).isNotEqualTo(newKey);
    assertThat(mapper.readTree(technical).get("proposalId").asString()).isEqualTo(id);
    assertThat(mapper.readTree(semantic).get("proposalId").asString()).isEqualTo(id);
    assertThat(vgv.count()).isEqualTo(countBefore + 1);
    assertThat(semantic).doesNotContain("52998224725");
    post(newKey, tenant, "16000", "2026-10-16").andExpect(status().isUnprocessableContent());
    post(key, tenant, "16000", "2026-10-16").andExpect(status().isUnprocessableContent());
    // A distinct intent is admitted here: margin reservation belongs to the downstream domain.
    post(UUID.randomUUID().toString(), tenant, "16000", "2026-10-16")
        .andExpect(header().string("X-Idempotency-Status", "NEW"));
  }

  @Test
  void isolates_partners_using_the_same_header_key() throws Exception {
    String key = UUID.randomUUID().toString();
    post(key, "a-" + UUID.randomUUID().toString().substring(0, 8), "1", "2026-10-16")
        .andExpect(header().string("X-Idempotency-Status", "NEW"));
    post(key, "b-" + UUID.randomUUID().toString().substring(0, 8), "1", "2026-10-16")
        .andExpect(header().string("X-Idempotency-Status", "NEW"));
  }

  @Test
  void admits_one_semantic_intent_for_concurrent_new_uuids() throws Exception {
    String tenant = "parallel-" + UUID.randomUUID().toString().substring(0, 8);
    var tasks = new ArrayList<Callable<String>>();
    for (int i = 0; i < 30; i++) {
      tasks.add(
          () ->
              post(UUID.randomUUID().toString(), tenant, "123.45", "2026-10-16")
                  .andExpect(status().isAccepted())
                  .andReturn()
                  .getResponse()
                  .getHeader("X-Idempotency-Status"));
    }
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var results = pool.invokeAll(tasks);
      var states = new ArrayList<String>();
      for (var result : results) {
        states.add(result.get());
      }
      assertThat(states.stream().filter("NEW"::equals).count()).isEqualTo(1);
      assertThat(states.stream().filter("DUPLICATE_REJECTED"::equals).count()).isEqualTo(29);
    }
  }
}
