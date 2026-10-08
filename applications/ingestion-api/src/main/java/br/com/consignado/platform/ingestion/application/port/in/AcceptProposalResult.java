package br.com.consignado.platform.ingestion.application.port.in;

import br.com.consignado.platform.ingestion.domain.IngestionOutcome;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import java.time.Instant;

/**
 * Resultado do aceite: para repeticoes, {@code tracingId} e {@code receivedAt} sao os originais.
 */
public record AcceptProposalResult(
    IngestionOutcome outcome,
    IdempotencyKey idempotencyKey,
    String tracingId,
    Instant receivedAt) {}
