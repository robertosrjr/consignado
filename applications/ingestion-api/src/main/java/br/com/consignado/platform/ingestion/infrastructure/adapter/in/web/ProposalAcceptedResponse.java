package br.com.consignado.platform.ingestion.infrastructure.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resposta 202: {@code RECEIVED} ou {@code DUPLICATE_ACCEPTED}, com o tracingId original. */
@Schema(description = "Proposta aceita para processamento")
public record ProposalAcceptedResponse(
    @Schema(example = "RECEIVED") String status,
    @Schema(example = "e3b0c442-98fc-11ee-b9d1-0242ac120002") String idempotencyKey,
    @Schema(example = "7d36ce98-0000-4000-8000-000000000001") String tracingId,
    @Schema(description = "Instante do recebimento (epoch em segundos)", example = "1772841600")
        long receivedAt) {}
