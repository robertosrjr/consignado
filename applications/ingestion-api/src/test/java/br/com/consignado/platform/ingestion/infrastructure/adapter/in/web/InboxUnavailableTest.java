package br.com.consignado.platform.ingestion.infrastructure.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.consignado.platform.ingestion.application.port.out.DurableInboxPort;
import br.com.consignado.platform.ingestion.domain.InboxUnavailableException;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Inbox indisponivel: a proposta NAO e aceita (503 + Retry-After), nunca um 202 enganoso. */
@SpringBootTest(
    properties = {
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false",
      "ingestion.inbox.type=memory",
      "ingestion.jwt.jwk-set-uri=http://localhost:9/jwks"
    })
@AutoConfigureMockMvc
@Import(InboxUnavailableTest.FailingInbox.class)
class InboxUnavailableTest {

  @TestConfiguration
  static class FailingInbox {
    @Bean
    @Primary
    DurableInboxPort failingInbox() {
      return item -> {
        throw new InboxUnavailableException("indisponivel", null);
      };
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add(
        "ingestion.payload.local-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  @Autowired MockMvc mvc;

  @Test
  void should_answer_503_with_retry_after_without_leaking_details() throws Exception {
    mvc.perform(
            post("/v1/proposals")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("X-Tenant-ID", "banco-a")
                .with(jwt().jwt(j -> j.claim("tenant", "banco-a")))
                .content(ProposalControllerTest.BODY))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "1"));
  }
}
