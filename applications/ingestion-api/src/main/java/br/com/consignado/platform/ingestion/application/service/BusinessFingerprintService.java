package br.com.consignado.platform.ingestion.application.service;

import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalCommand;
import br.com.consignado.platform.ingestion.application.port.out.BusinessFingerprintPort;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Function;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC with domain separation. The signer may use a non-exportable KMS HMAC key. */
public final class BusinessFingerprintService implements BusinessFingerprintPort {
  private final CanonicalPayloadNormalizer normalizer = new CanonicalPayloadNormalizer();
  private final Function<byte[], byte[]> signer;
  private final String version;

  public BusinessFingerprintService(Function<byte[], byte[]> signer, String version) {
    this.signer = Objects.requireNonNull(signer);
    if (version == null || !version.matches("[A-Za-z0-9_-]{1,32}")) {
      throw new IllegalArgumentException("Invalid fingerprint key version");
    }
    this.version = version;
  }

  /**
   * Explicit local/test key only. No fallback secret. A Mac is created per call for thread safety.
   */
  public static BusinessFingerprintService local(byte[] secret, String version) {
    if (secret == null || secret.length < 32) {
      throw new IllegalArgumentException("Fingerprint HMAC key must contain at least 32 bytes");
    }
    SecretKeySpec key = new SecretKeySpec(secret.clone(), "HmacSHA256");
    return new BusinessFingerprintService(
        data -> {
          try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(data);
          } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
          }
        },
        version);
  }

  @Override
  public Fingerprints generate(AcceptProposalCommand command, LocalDate receivedDateUtc) {
    LocalDate businessDate =
        command.contractDate() != null ? command.contractDate() : receivedDateUtc;
    String scope =
        command.tenant().value().length()
            + ":"
            + command.tenant().value()
            + "|"
            + command.portability()
            + "|";
    String business =
        digest("business-v2|" + scope + normalizer.buildCanonicalString(command, businessDate));
    // With an omitted date, technical retries stay stable across UTC midnight.
    String request =
        digest(
            "request-v2|POST /v1/proposals|"
                + scope
                + normalizer.buildCanonicalString(command, LocalDate.of(2000, 1, 1))
                + "|"
                + (command.contractDate() == null ? "implicit" : command.contractDate()));
    return new Fingerprints(business, request, businessDate);
  }

  private String digest(String canonical) {
    return version
        + ":"
        + HexFormat.of().formatHex(signer.apply(canonical.getBytes(StandardCharsets.UTF_8)));
  }
}
