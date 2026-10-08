package br.com.consignado.platform.worker.domain;

import br.com.consignado.platform.shared.vo.IdempotencyKey;
import java.time.Instant;

/**
 * Registro auditavel de uma transicao (outbox como historico, D12). {@code detail} nunca contem
 * PII.
 */
public record ProposalEvent(IdempotencyKey key, String type, Instant occurredAt, String detail) {

  public static final String CREATED = "PROPOSAL_CREATED";
  public static final String SENT_TO_LEGACY = "SENT_TO_LEGACY";
  public static final String CONFIRMED = "CONFIRMED";
  public static final String FAILED = "FAILED";

  public static ProposalEvent of(IdempotencyKey key, String type, Instant at, String detail) {
    return new ProposalEvent(key, type, at, detail);
  }
}
