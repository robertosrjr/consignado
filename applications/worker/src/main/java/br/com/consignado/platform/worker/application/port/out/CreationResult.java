package br.com.consignado.platform.worker.application.port.out;

import br.com.consignado.platform.worker.domain.Proposal;

/**
 * Resultado de {@code createPendingIfAbsent}: a proposta (nova ou existente) e se foi criada agora.
 */
public record CreationResult(Proposal proposal, boolean created) {}
