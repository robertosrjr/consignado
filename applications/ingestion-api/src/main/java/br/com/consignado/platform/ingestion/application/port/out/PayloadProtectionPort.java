package br.com.consignado.platform.ingestion.application.port.out;

import br.com.consignado.platform.shared.payload.InboxPayload;

/** Porta de saida: serializa, comprime e cifra os dados sensiveis (inclui o CPF). */
public interface PayloadProtectionPort {

  String protect(InboxPayload payload);
}
