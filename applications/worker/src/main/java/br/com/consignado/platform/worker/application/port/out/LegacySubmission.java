package br.com.consignado.platform.worker.application.port.out;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.vo.Cpf;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.Money;

/** Pedido de averbacao ao legado; a chave de idempotencia evita averbacao duplicada. */
public record LegacySubmission(
    IdempotencyKey idempotencyKey,
    Cpf cpf,
    ConvenioType convenio,
    Money amount,
    int installments) {}
