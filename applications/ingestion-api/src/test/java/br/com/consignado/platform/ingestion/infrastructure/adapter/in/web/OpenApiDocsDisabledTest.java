package br.com.consignado.platform.ingestion.infrastructure.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false"
    })
@AutoConfigureMockMvc
class OpenApiDocsDisabledTest {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add(
        "ingestion.payload.local-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  @Autowired MockMvc mvc;

  @Test
  void should_not_expose_the_docs_when_they_are_disabled() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().is4xxClientError());
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().is4xxClientError());
  }
}
