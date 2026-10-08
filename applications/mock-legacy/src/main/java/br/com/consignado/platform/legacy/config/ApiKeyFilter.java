package br.com.consignado.platform.legacy.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Exige o header {@code X-Api-Key} em {@code /v1/**} (negacao por padrao, SECURITY-08). A
 * comparacao e em tempo constante; a chave vem de configuracao, nunca do codigo.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

  static final String HEADER = "X-Api-Key";

  private final byte[] expected;

  public ApiKeyFilter(String apiKey) {
    this.expected = apiKey.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/v1/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String provided = request.getHeader(HEADER);
    if (provided == null
        || !MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expected)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("application/problem+json");
      response.setHeader("Cache-Control", "no-store");
      response
          .getWriter()
          .write("{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Credencial invalida\"}");
      return;
    }
    chain.doFilter(request, response);
  }
}
