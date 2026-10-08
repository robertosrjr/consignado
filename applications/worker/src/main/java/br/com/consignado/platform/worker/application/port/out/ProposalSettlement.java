package br.com.consignado.platform.worker.application.port.out;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.shared.vo.TenantId;
import java.time.Duration;

/**
 * Fato de negocio emitido quando uma proposta chega a um estado terminal no legado. Nao carrega
 * CPF; {@code timeToSettle} e o tempo desde o aceite na borda (nulo se o aceite nao for conhecido).
 */
public record ProposalSettlement(
    TenantId tenant,
    Lane lane,
    ConvenioType convenio,
    boolean portability,
    Money amount,
    int attempts,
    boolean confirmed,
    Duration timeToSettle) {}
