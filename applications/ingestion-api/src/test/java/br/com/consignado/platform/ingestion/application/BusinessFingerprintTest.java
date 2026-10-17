package br.com.consignado.platform.ingestion.application;

import static org.assertj.core.api.Assertions.*;

import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalCommand;
import br.com.consignado.platform.ingestion.application.service.BusinessFingerprintService;
import br.com.consignado.platform.ingestion.application.service.CanonicalPayloadNormalizer;
import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.vo.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusinessFingerprintTest {
  private static final LocalDate DAY = LocalDate.of(2026, 10, 16);
  private final BusinessFingerprintService service =
      BusinessFingerprintService.local(new byte[32], "v2");

  static AcceptProposalCommand command(
      String tenant,
      String cpf,
      String amount,
      boolean portability,
      int installments,
      LocalDate contractDate) {
    return new AcceptProposalCommand(
        new IdempotencyKey(UUID.randomUUID()),
        new TenantId(tenant),
        Cpf.of(cpf),
        ConvenioType.INSS,
        portability,
        Money.of(amount),
        installments,
        contractDate);
  }

  @Test
  void canonicalizes_validated_fields_without_locale_or_decimal_format_variations() {
    var a = command("a", "529.982.247-25", "15000.000", false, 84, null);
    var b = command("a", "52998224725", "15000", false, 84, null);
    assertThat(new CanonicalPayloadNormalizer().buildCanonicalString(a, DAY))
        .isEqualTo("52998224725|INSS|15000.00|84|2026-10-16");
    assertThat(service.generate(a, DAY)).isEqualTo(service.generate(b, DAY));
    assertThat(service.generate(a, DAY).business()).matches("v2:[0-9a-f]{64}");
    assertThat(service.generate(a, DAY).toString()).doesNotContain("52998224725");
    assertThatThrownBy(() -> new CanonicalPayloadNormalizer().buildCanonicalString(null, DAY))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new CanonicalPayloadNormalizer().buildCanonicalString(a, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void preserves_leading_cpf_zeros_and_rejects_invalid_money_instead_of_silent_rounding() {
    var c = command("a", "01234567890", "1.00", false, 1, DAY);
    assertThat(new CanonicalPayloadNormalizer().buildCanonicalString(c, DAY))
        .startsWith("01234567890|");
    assertThatThrownBy(() -> command("a", "52998224725", "1.001", false, 1, DAY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void separates_tenant_portability_amount_installments_date_and_secret() {
    var c = command("a", "52998224725", "15000", false, 84, DAY);
    String hash = service.generate(c, DAY).business();
    assertThat(
            service.generate(command("b", "52998224725", "15000", false, 84, DAY), DAY).business())
        .isNotEqualTo(hash);
    assertThat(
            service.generate(command("a", "52998224725", "15000", true, 84, DAY), DAY).business())
        .isNotEqualTo(hash);
    assertThat(
            service.generate(command("a", "52998224725", "15001", false, 84, DAY), DAY).business())
        .isNotEqualTo(hash);
    assertThat(
            service.generate(command("a", "52998224725", "15000", false, 85, DAY), DAY).business())
        .isNotEqualTo(hash);
    assertThat(
            service
                .generate(command("a", "52998224725", "15000", false, 84, DAY.plusDays(1)), DAY)
                .business())
        .isNotEqualTo(hash);
    byte[] anotherKey = new byte[32];
    Arrays.fill(anotherKey, (byte) 1);
    assertThat(BusinessFingerprintService.local(anotherKey, "v2").generate(c, DAY).business())
        .isNotEqualTo(hash);
  }

  @Test
  void
      technical_retry_without_contract_date_is_stable_across_midnight_but_business_window_changes() {
    var c = command("a", "52998224725", "15000", false, 84, null);
    var a = service.generate(c, DAY);
    var b = service.generate(c, DAY.plusDays(1));
    assertThat(a.request()).isEqualTo(b.request());
    assertThat(a.business()).isNotEqualTo(b.business());
    var explicit = command("a", "52998224725", "15000", false, 84, DAY);
    assertThat(service.generate(explicit, DAY).business()).isEqualTo(a.business());
    assertThat(service.generate(explicit, DAY.plusDays(1)))
        .isEqualTo(service.generate(explicit, DAY));
  }

  @Test
  void has_no_default_secret_and_validates_key_versions() {
    assertThatThrownBy(() -> BusinessFingerprintService.local(new byte[16], "v2"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> BusinessFingerprintService.local(null, "v2"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> BusinessFingerprintService.local(new byte[32], "bad|version"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
