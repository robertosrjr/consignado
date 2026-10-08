package br.com.consignado.platform.worker.application.port.out;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;

/** Dados minimos para criar a proposta em PENDING. */
public record ProposalSeed(IdempotencyKey key, TenantId tenant, Lane lane) {}
