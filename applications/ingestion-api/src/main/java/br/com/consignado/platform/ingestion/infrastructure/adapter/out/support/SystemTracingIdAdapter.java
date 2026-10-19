package br.com.consignado.platform.ingestion.infrastructure.adapter.out.support;

import br.com.consignado.platform.ingestion.application.port.out.TracingIdPort;
import java.util.UUID;

/**
 * Correlacao da aceitacao, independente do trace distribuido. Duas requisicoes podem compartilhar
 * traceId e ainda precisam de identidades distintas para distinguir retry do SDK de duplicata.
 */
public class SystemTracingIdAdapter implements TracingIdPort {

  @Override
  public String currentTracingId() {
    return UUID.randomUUID().toString();
  }
}
