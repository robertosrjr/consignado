package br.com.consignado.platform.localenv;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Emissor JWT <b>somente para desenvolvimento local</b> (sem Docker): publica um JWKS e emite
 * tokens RS256 com {@code iss}, {@code aud}, {@code exp} e a claim {@code tenant}. Escuta apenas no
 * loopback. A chave e gerada a cada inicio e nunca sai da memoria. Nao use fora do ambiente local.
 */
public final class LocalTokenIssuer implements AutoCloseable {

  private static final Pattern TENANT = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

  private final RSAKey key;
  private final HttpServer server;
  private final String issuer;
  private final String audience;

  private LocalTokenIssuer(RSAKey key, HttpServer server, String issuer, String audience) {
    this.key = key;
    this.server = server;
    this.issuer = issuer;
    this.audience = audience;
  }

  /** Inicia o emissor; {@code port} 0 escolhe uma porta livre. */
  public static LocalTokenIssuer start(int port, String audience)
      throws IOException, JOSEException {
    RSAKey key = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
    HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
    String issuer = "http://127.0.0.1:" + http.getAddress().getPort();
    LocalTokenIssuer issuerInstance = new LocalTokenIssuer(key, http, issuer, audience);
    http.createContext("/jwks", issuerInstance::jwks);
    http.createContext("/token", issuerInstance::token);
    http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    http.start();
    return issuerInstance;
  }

  public String issuer() {
    return issuer;
  }

  public String audience() {
    return audience;
  }

  public String jwksUri() {
    return issuer + "/jwks";
  }

  /** Emite um token RS256 para o tenant, valido pela duracao informada. */
  public String issueToken(String tenant, Duration validity) throws JOSEException {
    if (tenant == null || !TENANT.matcher(tenant).matches()) {
      throw new IllegalArgumentException("tenant invalido");
    }
    Instant now = Instant.now();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject("robot-" + tenant)
            .claim("tenant", tenant)
            .issueTime(Date.from(now))
            .notBeforeTime(Date.from(now.minusSeconds(5)))
            .expirationTime(Date.from(now.plus(validity)))
            .jwtID(UUID.randomUUID().toString())
            .build();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID())
                .type(JOSEObjectType.JWT)
                .build(),
            claims);
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }

  private void jwks(HttpExchange exchange) throws IOException {
    respond(exchange, 200, new JWKSet(key.toPublicJWK()).toString());
  }

  /** {@code GET|POST /token?tenant=banco-a[&ttl=3600]}. */
  private void token(HttpExchange exchange) throws IOException {
    String query = exchange.getRequestURI().getRawQuery();
    String tenant = param(query, "tenant");
    long ttl = 3600;
    String ttlParam = param(query, "ttl");
    if (ttlParam != null) {
      try {
        ttl = Math.min(Math.max(Long.parseLong(ttlParam), 1), 86_400);
      } catch (NumberFormatException e) {
        respond(exchange, 400, "{\"error\":\"ttl invalido\"}");
        return;
      }
    }
    try {
      String jwt = issueToken(tenant, Duration.ofSeconds(ttl));
      respond(
          exchange,
          200,
          "{\"access_token\":\""
              + jwt
              + "\",\"token_type\":\"Bearer\",\"expires_in\":"
              + ttl
              + "}");
    } catch (IllegalArgumentException e) {
      respond(exchange, 400, "{\"error\":\"tenant invalido\"}");
    } catch (JOSEException e) {
      respond(exchange, 500, "{\"error\":\"falha ao assinar\"}");
    }
  }

  private static String param(String query, String name) {
    if (query == null) {
      return null;
    }
    for (String pair : query.split("&")) {
      int eq = pair.indexOf('=');
      if (eq > 0 && pair.substring(0, eq).equals(name)) {
        return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
      }
    }
    return null;
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.getResponseHeaders().add("Cache-Control", "no-store");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
