package br.com.consignado.platform.ingestion.infrastructure.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "ingestion.inbox.type=memory",
      "ingestion.jwt.jwk-set-uri=http://localhost:9/jwks",
      "springdoc.api-docs.enabled=true",
      "springdoc.swagger-ui.enabled=true"
    })
@AutoConfigureMockMvc
class OpenApiDocsEnabledTest {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add(
        "ingestion.payload.local-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  @Autowired MockMvc mvc;

  @Test
  void should_serve_the_public_api_docs_describing_the_proposal_endpoint() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/v1/proposals'].post").exists())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
  }

  @Test
  void should_serve_the_swagger_ui() throws Exception {
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }
}
