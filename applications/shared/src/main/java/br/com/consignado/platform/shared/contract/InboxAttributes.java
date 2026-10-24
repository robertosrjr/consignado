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
  public static final String LANE = "lane";
  public static final String ROUTED_AT = "routedAt";
  public static final String PAYLOAD = "payload";
  public static final String TRACING_ID = "tracingId";
  public static final String STATUS = "status";
  public static final String CREATED_AT = "createdAt";
  public static final String TTL = "ttl";

  /**
   * Medicao do SLO J2. {@code sloOpen} (lane) existe apenas enquanto a proposta roteada nao foi
   * avaliada contra o prazo; e a chave do indice esparso {@link #SLO_OPEN_INDEX} (com {@code
   * createdAt}). O Worker grava {@code decision}/{@code decidedAt}; o avaliador remove {@code
   * sloOpen} e grava {@code sloOutcome}.
   */
  public static final String SLO_OPEN = "sloOpen";

  public static final String SLO_OPEN_INDEX = "sloOpen-createdAt";
  public static final String SLO_OUTCOME = "sloOutcome";
  public static final String DECISION = "decision";
  public static final String DECIDED_AT = "decidedAt";

  public static final String DECISION_CONFIRMED = "confirmed";
  public static final String DECISION_BUSINESS_REJECTED = "business_rejected";

  /**
   * Marca esparsa de "aceito e ainda nao despachado" (valor {@link #DISPATCH_PENDING_VALUE}),
   * gravada pela Ingestion API e removida pelo Dispatcher. Com {@code createdAt}, forma o indice
   * {@link #DISPATCH_PENDING_INDEX} usado pelo Sweeper para recuperar itens orfaos (Stream perdido
   * ou SQS indisponivel no despacho).
   */
  public static final String DISPATCH_PENDING = "dispatchPending";

  public static final String DISPATCH_PENDING_VALUE = "pending";
  public static final String DISPATCH_PENDING_INDEX = "dispatchPending-createdAt";

  public static final String STATUS_RECEIVED = "RECEIVED";
  public static final String STATUS_DISPATCHED = "DISPATCHED";
  public static final String STATUS_FAILED = "FAILED";

  private InboxAttributes() {}
}
