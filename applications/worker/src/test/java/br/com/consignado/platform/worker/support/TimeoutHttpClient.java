package br.com.consignado.platform.worker.support;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Deterministic timeout injection; retains the actual request for contract assertions. */
public final class TimeoutHttpClient extends HttpClient {
  public HttpRequest lastRequest;
  private final HttpClient metadata = HttpClient.newHttpClient();

  @Override
  public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
      throws IOException {
    lastRequest = request;
    throw new HttpTimeoutException("simulated timeout");
  }

  @Override
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(
      HttpRequest request, HttpResponse.BodyHandler<T> handler) {
    return CompletableFuture.failedFuture(new HttpTimeoutException("simulated timeout"));
  }

  @Override
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(
      HttpRequest request,
      HttpResponse.BodyHandler<T> handler,
      HttpResponse.PushPromiseHandler<T> pushHandler) {
    return sendAsync(request, handler);
  }

  @Override
  public Optional<java.net.CookieHandler> cookieHandler() {
    return metadata.cookieHandler();
  }

  @Override
  public Optional<java.time.Duration> connectTimeout() {
    return metadata.connectTimeout();
  }

  @Override
  public Redirect followRedirects() {
    return metadata.followRedirects();
  }

  @Override
  public Optional<java.net.ProxySelector> proxy() {
    return metadata.proxy();
  }

  @Override
  public javax.net.ssl.SSLContext sslContext() {
    return metadata.sslContext();
  }

  @Override
  public javax.net.ssl.SSLParameters sslParameters() {
    return metadata.sslParameters();
  }

  @Override
  public Optional<java.net.Authenticator> authenticator() {
    return metadata.authenticator();
  }

  @Override
  public Version version() {
    return metadata.version();
  }

  @Override
  public Optional<java.util.concurrent.Executor> executor() {
    return metadata.executor();
  }
}
