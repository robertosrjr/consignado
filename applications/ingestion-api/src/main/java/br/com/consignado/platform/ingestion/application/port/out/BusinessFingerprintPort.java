package br.com.consignado.platform.ingestion.application.port.out;

import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalCommand;
import java.time.LocalDate;

/** Internal fingerprints; neither digests nor canonical payloads belong in telemetry. */
public interface BusinessFingerprintPort {
  Fingerprints generate(AcceptProposalCommand command, LocalDate receivedDateUtc);

  record Fingerprints(String business, String request, LocalDate businessDate) {
    public Fingerprints(String business, String request) {
      this(business, request, null);
    }

    @Override
    public String toString() {
      return "Fingerprints[redacted]";
    }
  }
}
