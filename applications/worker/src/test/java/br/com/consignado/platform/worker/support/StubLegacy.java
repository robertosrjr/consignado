package br.com.consignado.platform.worker.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Legado simulado em HTTP real (JDK HttpServer) para os testes do Worker: registra chamadas,
 * cabecalhos, corpo, chaves unicas e duplicadas, instantes de chegada (para medir o pico por janela
 * de 1 s) e permite forcar status, atraso e falha unica por chave.
 */
public final class StubLegacy implements AutoCloseable {

  private final HttpServer server;
  public volatile int status = 200;
  public volatile long delayMillis = 0;
  public volatile String body;
  public final AtomicInteger requests = new AtomicInteger();
  public final AtomicInteger duplicateRequests = new AtomicInteger();
  public final Set<String> uniqueKeys = ConcurrentHashMap.newKeySet();
  public final Set<String> failOnceFor = ConcurrentHashMap.newKeySet();
  private final Map<String, AtomicInteger> requestsByKey = new ConcurrentHashMap<>();
  public final List<Long> arrivals = Collections.synchronizedList(new ArrayList<>());
  public volatile Map<String, String> lastHeaders = Map.of();
  public volatile String lastBody = "";

  private StubLegacy(HttpServer server) {
    this.server = server;
  }

  public static StubLegacy start() {
    try {
      HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      StubLegacy stub = new StubLegacy(http);
      http.createContext("/v1/averbacoes", stub::handle);
      http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
      http.start();
      return stub;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private void handle(HttpExchange exchange) throws IOException {
    arrivals.add(System.nanoTime());
    requests.incrementAndGet();
    String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
    requestsByKey.computeIfAbsent(String.valueOf(key), k -> new AtomicInteger()).incrementAndGet();
    lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    lastHeaders =
        Map.of(
            "Idempotency-Key", String.valueOf(key),
            "X-Api-Key", String.valueOf(exchange.getRequestHeaders().getFirst("X-Api-Key")),
            "Content-Type", String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type")),
            "Method", exchange.getRequestMethod(),
            "Path", exchange.getRequestURI().getPath());
    if (key != null && failOnceFor.remove(key)) {
      respond(exchange, 503, "{\"title\":\"indisponivel\"}");
      return;
    }
    if (delayMillis > 0) {
      try {
        Thread.sleep(delayMillis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    if (status == 200 && key != null && !uniqueKeys.add(key)) {
      duplicateRequests.incrementAndGet();
    }
    String response =
        body != null
            ? body
            : "{\"averbacaoId\":\""
                + UUID.nameUUIDFromBytes(String.valueOf(key).getBytes(StandardCharsets.UTF_8))
                + "\",\"status\":\"AVERBADA\"}";
    respond(exchange, status, response);
  }

  private static void respond(HttpExchange exchange, int code, String payload) throws IOException {
    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(code, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  /** Requisicoes recebidas com a chave de idempotencia informada. */
  public int requestsFor(String key) {
    AtomicInteger count = requestsByKey.get(key);
    return count == null ? 0 : count.get();
  }

  /** Maior numero de requisicoes em qualquer janela de {@code windowNanos}. */
  public int maxRequestsInAnyWindow(long windowNanos) {
    List<Long> snapshot;
    synchronized (arrivals) {
      snapshot = new ArrayList<>(arrivals);
    }
    Collections.sort(snapshot);
    int max = 0;
    int start = 0;
    for (int end = 0; end < snapshot.size(); end++) {
      while (snapshot.get(end) - snapshot.get(start) >= windowNanos) {
        start++;
      }
      max = Math.max(max, end - start + 1);
    }
    return max;
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
