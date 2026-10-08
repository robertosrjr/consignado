package br.com.consignado.platform.ingestion.application.port.in;

/** Porta de entrada: aceita uma proposta com durabilidade e idempotencia. */
public interface AcceptProposalUseCase {

  AcceptProposalResult accept(AcceptProposalCommand command);
}
