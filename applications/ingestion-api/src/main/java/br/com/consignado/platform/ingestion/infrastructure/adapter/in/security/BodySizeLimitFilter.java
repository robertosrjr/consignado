package br.com.consignado.platform.ingestion.infrastructure.adapter.in.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite de tamanho do corpo (SECURITY-05): rejeita com 413 requisicoes cujo Content-Length excede
 * o maximo ou que usam transferencia sem tamanho declarado em {@code /v1/**}.
 */
public class BodySizeLimitFilter extends OncePerRequestFilter {

  private final long maxBytes;

  public BodySizeLimitFilter(long maxBytes) {
    this.maxBytes = maxBytes;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/v1/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long length = request.getContentLengthLong();
    if (length > maxBytes) {
      response.setStatus(413);
      response.setContentType("application/problem+json");
      response.setHeader("Cache-Control", "no-store");
      response
          .getWriter()
          .write(
              "{\"title\":\"Content Too Large\",\"status\":413,\"detail\":\"Corpo excede o limite\"}");
      return;
    }
    chain.doFilter(request, response);
  }
}
