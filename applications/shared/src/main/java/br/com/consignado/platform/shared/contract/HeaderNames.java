package br.com.consignado.platform.shared.contract;

/** Headers HTTP e atributos de mensagem comuns (inclui propagacao W3C Trace Context). */
public final class HeaderNames {

  public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
  public static final String TENANT_ID = "X-Tenant-ID";
  public static final String TRACEPARENT = "traceparent";
  public static final String TRACESTATE = "tracestate";

  /** Nome do atributo de mensagem SQS que transporta o traceparent. */
  public static final String MESSAGE_ATTRIBUTE_TRACEPARENT = TRACEPARENT;

  private HeaderNames() {}
}
