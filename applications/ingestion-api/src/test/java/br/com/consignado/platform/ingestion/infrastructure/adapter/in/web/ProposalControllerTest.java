package br.com.consignado.platform.ingestion.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false",
      "ingestion.inbox.type=memory",
      "ingestion.jwt.jwk-set-uri=http://localhost:9/jwks",
      "ingestion.quota.default-per-second=1000",
      "ingestion.quota.tenants.banco-b=1",
      "platform.metrics.tenant-allowlist=banco-a"
    })
@AutoConfigureMockMvc
class ProposalControllerTest {

  static final String CPF = "52998224725";
  static final String BODY =
      "{\"customerCpf\":\"52998224725\",\"convenioCode\":\"INSS\",\"isPortability\":false,"
          + "\"amount\":15000.50,\"installments\":84}";

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add(
        "ingestion.payload.local-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  @Autowired MockMvc mvc;
  @Autowired MeterRegistry registry;

  @Autowired io.opentelemetry.api.OpenTelemetry telemetry;

  @Test
  void should_configure_real_tracing_without_a_remote_collector() {
    var span = telemetry.getTracer("test").spanBuilder("local-check").startSpan();
    try {
      assertThat(span.getSpanContext().isValid()).isTrue();
    } finally {
      span.end();
    }
  }

  private ResultActions post(String key, String tenantHeader, String tenantClaim, String body)
      throws Exception {
    MockHttpServletRequestBuilder request =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/proposals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    if (key != null) {
      request.header("Idempotency-Key", key);
    }
    if (tenantHeader != null) {
      request.header("X-Tenant-ID", tenantHeader);
    }
    if (tenantClaim != null) {
      request.with(jwt().jwt(j -> j.claim("tenant", tenantClaim)));
    }
    return mvc.perform(request);
  }

  private ResultActions ok(String key) throws Exception {
    return post(key, "banco-a", "banco-a", BODY);
  }

  private static String newKey() {
    return UUID.randomUUID().toString();
  }

  @Test
  void should_answer_202_received_with_the_contract_fields() throws Exception {
    String key = newKey();

    ok(key)
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("RECEIVED"))
        .andExpect(jsonPath("$.idempotencyKey").value(key))
        .andExpect(jsonPath("$.tracingId").isNotEmpty())
        .andExpect(jsonPath("$.receivedAt").isNumber())
        .andExpect(header().exists("X-Trace-ID"));
  }

  @Test
  void should_never_return_the_cpf_in_any_response() throws Exception {
    String accepted = ok(newKey()).andReturn().getResponse().getContentAsString();
    String invalid =
        post(newKey(), "banco-a", "banco-a", BODY.replace(CPF, "52998224726"))
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(accepted).doesNotContain(CPF);
    assertThat(invalid).doesNotContain("52998224726").doesNotContain(CPF);
  }

  @Test
  void should_answer_202_duplicate_accepted_with_the_same_tracing_id_for_a_repeated_key()
      throws Exception {
    String key = newKey();
    String first = ok(key).andReturn().getResponse().getContentAsString();

    ok(key)
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("DUPLICATE_ACCEPTED"))
        .andExpect(
            jsonPath("$.tracingId")
                .value(
                    org.hamcrest.Matchers.equalTo(
                        com.jayway.jsonpath.JsonPath.read(first, "$.tracingId"))));
  }

  @Test
  void should_add_vgv_only_for_new_proposals_not_for_duplicates() throws Exception {
    var vgv = registry.summary("business.vgv.received", "convenio", "inss", "portability", "false");
    double countBefore = vgv.count();
    double sumBefore = vgv.totalAmount();
    String key = newKey();

    ok(key);
    ok(key);

    assertThat((double) vgv.count()).isEqualTo(countBefore + 1);
    assertThat(vgv.totalAmount()).isEqualTo(sumBefore + 15000.50);
    assertThat(registry.counter("business.partner.received", "tenant", "banco-a").count())
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  void should_count_accepted_proposals_by_outcome() throws Exception {
    double before = registry.counter("ingestion.accepted", "outcome", "received").count();
    String key = newKey();

    ok(key);
    ok(key);

    assertThat(registry.counter("ingestion.accepted", "outcome", "received").count())
        .isEqualTo(before + 1);
    assertThat(registry.counter("ingestion.accepted", "outcome", "duplicate_accepted").count())
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  void should_answer_401_problem_json_without_a_token() throws Exception {
    post(newKey(), "banco-a", null, BODY)
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  void should_answer_403_when_the_tenant_header_differs_from_the_token_tenant() throws Exception {
    post(newKey(), "banco-z", "banco-a", BODY).andExpect(status().isForbidden());
  }

  @Test
  void should_answer_400_for_invalid_headers() throws Exception {
    post("nao-e-uuid", "banco-a", "banco-a", BODY).andExpect(status().isBadRequest());
    post(null, "banco-a", "banco-a", BODY).andExpect(status().isBadRequest());
    post(newKey(), "tenant invalido", "banco-a", BODY).andExpect(status().isBadRequest());
    post(newKey(), null, "banco-a", BODY).andExpect(status().isBadRequest());
  }

  @Test
  void should_answer_400_problem_json_for_invalid_payloads() throws Exception {
    String[] invalid = {
      BODY.replace(CPF, "52998224726"),
      BODY.replace(CPF, "abc"),
      BODY.replace("INSS", "OUTRO"),
      BODY.replace("15000.50", "-1"),
      BODY.replace("15000.50", "1.234"),
      BODY.replace("\"installments\":84", "\"installments\":0"),
      BODY.replace("\"installments\":84", "\"installments\":121"),
      BODY.replace("\"customerCpf\":\"52998224725\",", ""),
      "{",
      ""
    };
    for (String body : invalid) {
      post(newKey(), "banco-a", "banco-a", body)
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
  }

  @Test
  void should_answer_429_with_retry_after_for_a_tenant_over_its_quota_and_count_it()
      throws Exception {
    double before = registry.counter("ingestion.throttled.tenant", "tenant", "other").count();
    int throttled = 0;
    for (int i = 0; i < 4; i++) {
      var result = post(newKey(), "banco-b", "banco-b", BODY).andReturn().getResponse();
      if (result.getStatus() == 429) {
        throttled++;
        assertThat(result.getHeader("Retry-After")).isEqualTo("1");
      }
    }

    assertThat(throttled).isGreaterThanOrEqualTo(1);
    assertThat(registry.counter("ingestion.throttled.tenant", "tenant", "other").count())
        .isGreaterThan(before);
  }

  @Test
  void should_not_throttle_other_tenants_while_one_is_over_its_quota() throws Exception {
    for (int i = 0; i < 4; i++) {
      post(newKey(), "banco-b", "banco-b", BODY);
    }

    ok(newKey()).andExpect(status().isAccepted());
  }

  @Test
  void should_answer_413_when_the_body_exceeds_the_limit() throws Exception {
    post(newKey(), "banco-a", "banco-a", "x".repeat(20_000)).andExpect(status().is(413));
  }

  @Test
  void should_deny_everything_else_and_expose_only_health_without_authentication()
      throws Exception {
    mvc.perform(get("/v1/desconhecido")).andExpect(status().isUnauthorized());
    mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }
}
