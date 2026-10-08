package br.com.consignado.platform.ingestion.application.port.out;

import java.time.Instant;

/** Porta de saida: tempo testavel. */
public interface ClockPort {

  Instant now();
}
