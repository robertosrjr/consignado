package br.com.consignado.platform.ingestion.infrastructure.adapter.in.security;

import br.com.consignado.platform.ingestion.infrastructure.config.IngestionProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Resource Server stateless: toda rota exige autenticacao (negacao por padrao); somente a saude e a
 * coleta de metricas sao publicas (e devem ficar restritas por rede/ALB). O JWT e validado a cada
 * requisicao (assinatura via JWKS, expiracao, emissor e audiencia).
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, @Value("${springdoc.api-docs.enabled:false}") boolean docsEnabled)
      throws Exception {
    AuthenticationEntryPoint unauthorized =
        (request, response, ex) -> {
          response.setStatus(401);
          response.setContentType("application/problem+json");
          response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
          response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
          response
              .getWriter()
              .write(
                  "{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Credenciais ausentes ou invalidas\"}");
        };
    AccessDeniedHandler forbidden =
        (request, response, ex) -> {
          response.setStatus(403);
          response.setContentType("application/problem+json");
          response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
          response
              .getWriter()
              .write("{\"title\":\"Forbidden\",\"status\":403,\"detail\":\"Acesso negado\"}");
        };
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll();
              if (docsEnabled) {
                // documentacao interativa: somente quando springdoc esta habilitado (dev/local)
                auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll();
              }
              auth.requestMatchers("/v1/**").authenticated().anyRequest().denyAll();
            })
        .oauth2ResourceServer(
            oauth -> oauth.jwt(Customizer.withDefaults()).authenticationEntryPoint(unauthorized))
        .exceptionHandling(
            e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden));
    return http.build();
  }

  @Bean
  JwtDecoder jwtDecoder(IngestionProperties properties) {
    IngestionProperties.Jwt jwt = properties.jwt();
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwt.jwkSetUri()).build();
    List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
    validators.add(
        jwt.issuer() != null && !jwt.issuer().isBlank()
            ? JwtValidators.createDefaultWithIssuer(jwt.issuer())
            : JwtValidators.createDefault());
    if (jwt.audience() != null && !jwt.audience().isBlank()) {
      validators.add(
          new JwtClaimValidator<List<String>>(
              JwtClaimNames.AUD, aud -> aud != null && aud.contains(jwt.audience())));
    }
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
    return decoder;
  }

  @Bean
  FilterRegistrationBean<BodySizeLimitFilter> bodySizeLimitFilter(IngestionProperties properties) {
    FilterRegistrationBean<BodySizeLimitFilter> registration =
        new FilterRegistrationBean<>(new BodySizeLimitFilter(properties.maxBodyBytes()));
    registration.addUrlPatterns("/v1/*");
    registration.setOrder(0);
    return registration;
  }
}
