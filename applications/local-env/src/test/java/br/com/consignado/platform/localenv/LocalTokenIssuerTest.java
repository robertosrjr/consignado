package br.com.consignado.platform.localenv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalTokenIssuerTest {

  private LocalTokenIssuer issuer;
  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void start() throws Exception {
    issuer = LocalTokenIssuer.start(0, "consignado-ingestion");
  }

  @AfterEach
  void stop() {
    issuer.close();
  }

  private HttpResponse<String> get(String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create(issuer.issuer() + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void should_issue_tokens_that_verify_against_the_published_jwks() throws Exception {
    String token = issuer.issueToken("banco-a", Duration.ofMinutes(10));

    JWKSet jwks = JWKSet.parse(get("/jwks").body());
    RSAKey publicKey = (RSAKey) jwks.getKeys().get(0);
    SignedJWT jwt = SignedJWT.parse(token);

    assertThat(jwt.verify(new RSASSAVerifier(publicKey))).isTrue();
    assertThat(jwt.getHeader().getKeyID()).isEqualTo(publicKey.getKeyID());
    assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(issuer.issuer());
    assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("consignado-ingestion");
    assertThat(jwt.getJWTClaimsSet().getStringClaim("tenant")).isEqualTo("banco-a");
    assertThat(jwt.getJWTClaimsSet().getExpirationTime()).isAfter(new Date());
  }

  @Test
  void should_publish_only_public_key_material() throws Exception {
    String jwks = get("/jwks").body();

    assertThat(jwks).contains("\"kty\":\"RSA\"").doesNotContain("\"d\":").doesNotContain("\"p\":");
  }

  @Test
  void should_serve_tokens_over_http_with_a_bounded_ttl() throws Exception {
    HttpResponse<String> response = get("/token?tenant=banco-b&ttl=999999");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"access_token\":\"").contains("\"expires_in\":86400");
  }

  @Test
  void should_reject_invalid_tenants_and_ttls() throws Exception {
    assertThat(get("/token?tenant=tenant%20ruim").statusCode()).isEqualTo(400);
    assertThat(get("/token").statusCode()).isEqualTo(400);
    assertThat(get("/token?tenant=banco-a&ttl=abc").statusCode()).isEqualTo(400);
    assertThatThrownBy(() -> issuer.issueToken("a;b", Duration.ofMinutes(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_expose_the_issuer_audience_and_jwks_uri() {
    assertThat(issuer.audience()).isEqualTo("consignado-ingestion");
    assertThat(issuer.jwksUri()).isEqualTo(issuer.issuer() + "/jwks");
    assertThat(issuer.issuer()).startsWith("http://127.0.0.1:");
  }
}
