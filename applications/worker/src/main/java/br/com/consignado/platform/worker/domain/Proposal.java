package br.com.consignado.platform.worker.domain;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;
import java.time.Instant;

/**
 * Agregado da proposta no Worker (imutavel; cada transicao devolve uma nova instancia). Nao guarda
 * CPF nem valores: so identificadores tecnicos, estado e instantes (o dado sensivel permanece no
 * DurableInbox, cifrado).
 *
 * <p>Maquina de estados: PENDING -> SENT_TO_LEGACY -> CONFIRMED | FAILED; SENT_TO_LEGACY ->
 * SENT_TO_LEGACY (reenvio com a mesma chave); estados terminais sao absorventes.
 */
public record Proposal(
    IdempotencyKey key,
    TenantId tenant,
    Lane lane,
    ProposalStatus status,
    int attempts,
    String averbacaoId,
    String failureReason,
    Instant createdAt,
    Instant updatedAt) {

  public static final int MAX_REASON_LENGTH = 200;

  public Proposal {
    if (key == null || tenant == null || lane == null || status == null) {
      throw new IllegalArgumentException("Proposta invalida");
    }
  }

  public static Proposal pending(IdempotencyKey key, TenantId tenant, Lane lane, Instant now) {
    return new Proposal(key, tenant, lane, ProposalStatus.PENDING, 0, null, null, now, now);
  }

  public boolean isFinal() {
    return status.isFinal();
  }

  /** Registra o envio ao legado; permitido de PENDING e de SENT_TO_LEGACY (reenvio idempotente). */
  public Proposal markSentToLegacy(Instant now) {
    if (status != ProposalStatus.PENDING && status != ProposalStatus.SENT_TO_LEGACY) {
      throw new IllegalStateTransitionException(status, "enviar ao legado");
    }
    return new Proposal(
        key, tenant, lane, ProposalStatus.SENT_TO_LEGACY, attempts + 1, null, null, createdAt, now);
  }

  public Proposal confirm(String legacyAverbacaoId, Instant now) {
    if (status != ProposalStatus.SENT_TO_LEGACY) {
      throw new IllegalStateTransitionException(status, "confirmar");
    }
    return new Proposal(
        key,
        tenant,
        lane,
        ProposalStatus.CONFIRMED,
        attempts,
        legacyAverbacaoId,
        null,
        createdAt,
        now);
  }

  public Proposal fail(String reason, Instant now) {
    if (status != ProposalStatus.SENT_TO_LEGACY) {
      throw new IllegalStateTransitionException(status, "falhar");
    }
    String bounded =
        reason == null
            ? "unspecified"
            : reason.substring(0, Math.min(reason.length(), MAX_REASON_LENGTH));
    return new Proposal(
        key, tenant, lane, ProposalStatus.FAILED, attempts, null, bounded, createdAt, now);
  }
}
