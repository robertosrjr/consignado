package br.com.consignado.platform.ingestion.application.port.out;

/** Porta de saida: identificador de rastreio devolvido ao parceiro. */
public interface TracingIdPort {

  String currentTracingId();
}
