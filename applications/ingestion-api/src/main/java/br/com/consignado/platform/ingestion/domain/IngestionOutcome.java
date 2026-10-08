package br.com.consignado.platform.ingestion.domain;

/** Resultado da ingestao: primeira vez ({@code RECEIVED}) ou repeticao aceita sem reprocessar. */
public enum IngestionOutcome {
  RECEIVED,
  DUPLICATE_ACCEPTED
}
