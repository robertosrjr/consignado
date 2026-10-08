package br.com.consignado.platform.shared.contract;

/**
 * Nomes dos atributos do item do DurableInbox (DynamoDB). Compartilhado por Ingestion API
 * (escreve), Dispatcher (atualiza) e Worker (le). Atributos de roteamento ficam fora do payload
 * cifrado.
 */
public final class InboxAttributes {

  public static final String TABLE_NAME = "DurableInbox";

  public static final String IDEMPOTENCY_KEY = "idempotencyKey";
  public static final String TENANT_ID = "tenantId";
  public static final String CONVENIO_TYPE = "convenioType";
  public static final String PORTABILITY = "portability";
  public static final String AMOUNT = "amount";
  public static final String PRIORITY_SCORE = "priorityScore";
  public static final String PAYLOAD = "payload";
  public static final String TRACING_ID = "tracingId";
  public static final String STATUS = "status";
  public static final String CREATED_AT = "createdAt";
  public static final String TTL = "ttl";

  public static final String STATUS_RECEIVED = "RECEIVED";
  public static final String STATUS_DISPATCHED = "DISPATCHED";
  public static final String STATUS_FAILED = "FAILED";

  private InboxAttributes() {}
}
