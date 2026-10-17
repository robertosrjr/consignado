package br.com.consignado.platform.ingestion.application.service;

import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalCommand;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/** Operates on validated value objects, preserving the API's CPF/amount validation. */
public final class CanonicalPayloadNormalizer {
  public String buildCanonicalString(AcceptProposalCommand command, LocalDate dateUtc) {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(dateUtc, "dateUtc");
    return String.join(
        "|",
        command.cpf().digits(),
        command.convenio().name(),
        command.amount().amount().setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
        Integer.toString(command.installments()),
        dateUtc.toString());
  }
}
