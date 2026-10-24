package br.com.consignado.platform.localenv.incident;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Substituto local do SNS: recebe o webhook do Alertmanager em {@code POST /alerts} e publica na
 * fila de alertas um envelope igual ao do SNS ({@code Type=Notification}, {@code Message}). O corpo
 * do webhook ja tem os campos do template {@code consignado.sns.message}. Somente loopback.
 */
public final class AlertWebhookBridge implements AutoCloseable {

  static final int MAX_BODY_BYTES = 256 * 1024;
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final HttpServer server;

  private AlertWebhookBridge(HttpServer server) {
    this.server = server;
  }

  public static AlertWebhookBridge start(int port, SqsClient sqs, String queueUrl)
      throws IOException {
    HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
    http.createContext("/alerts", exchange -> handle(exchange, sqs, queueUrl));
    http.start();
    return new AlertWebhookBridge(http);
  }

  public String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/alerts";
  }

  private static void handle(HttpExchange exchange, SqsClient sqs, String queueUrl)
      throws IOException {
    try (exchange) {
      if (!"POST".equals(exchange.getRequestMethod())) {
        exchange.sendResponseHeaders(405, -1);
        return;
      }
      byte[] body;
      try (InputStream in = exchange.getRequestBody()) {
        body = in.readNBytes(MAX_BODY_BYTES + 1);
      }
      if (body.length > MAX_BODY_BYTES) {
        exchange.sendResponseHeaders(413, -1);
        return;
      }
      String message = new String(body, StandardCharsets.UTF_8);
      try {
        JSON.readTree(message);
      } catch (RuntimeException invalid) {
        exchange.sendResponseHeaders(400, -1);
        return;
      }
      Map<String, String> envelope = new LinkedHashMap<>();
      envelope.put("Type", "Notification");
      envelope.put("Subject", "consignado-alert");
      envelope.put("Message", message);
      try {
        sqs.sendMessage(
            SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(JSON.writeValueAsString(envelope))
                .build());
      } catch (SdkException e) {
        exchange.sendResponseHeaders(503, -1); // Alertmanager reenvia
        return;
      }
      exchange.sendResponseHeaders(202, -1);
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
