package br.com.consignado.platform.shared.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.contract.MessageContractException;
import br.com.consignado.platform.shared.vo.Cpf;
import br.com.consignado.platform.shared.vo.Money;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class InboxPayloadCodecTest {

  private static final String JSON =
      "{\"schemaVersion\":1,\"cpf\":\"52998224725\",\"convenio\":\"INSS\","
          + "\"portability\":true,\"amount\":\"15000.50\",\"installments\":84}";

  private static final InboxPayload PAYLOAD =
      InboxPayload.of(new Cpf("52998224725"), ConvenioType.INSS, true, Money.of("15000.50"), 84);

  @Test
  void should_serialize_to_the_flat_wire_format() {
    assertThat(new String(InboxPayloadCodec.toBytes(PAYLOAD), StandardCharsets.UTF_8))
        .isEqualTo(JSON);
  }

  @Test
  void should_deserialize_the_wire_format() {
    assertThat(InboxPayloadCodec.fromBytes(JSON.getBytes(StandardCharsets.UTF_8)))
        .isEqualTo(PAYLOAD);
  }

  @Test
  void should_mask_the_cpf_in_to_string() {
    assertThat(PAYLOAD.toString()).doesNotContain("52998224725").contains("***.***.***-25");
  }

  @Test
  void should_reject_invalid_content_without_leaking_it() {
    assertThatThrownBy(() -> InboxPayloadCodec.fromBytes("{".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(MessageContractException.class);
    byte[] badCpf = JSON.replace("52998224725", "52998224726").getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> InboxPayloadCodec.fromBytes(badCpf))
        .isInstanceOf(MessageContractException.class)
        .hasMessageNotContaining("5299822472");
    byte[] zeroInstallments = JSON.replace("84", "0").getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> InboxPayloadCodec.fromBytes(zeroInstallments))
        .isInstanceOf(MessageContractException.class);
  }

  @Test
  void should_validate_payload_invariants() {
    assertThatThrownBy(
            () ->
                InboxPayload.of(
                    new Cpf("52998224725"), ConvenioType.INSS, false, Money.of("1"), 121))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new InboxPayload(
                    2, new Cpf("52998224725"), ConvenioType.INSS, false, Money.of("1"), 12))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> InboxPayload.of(null, ConvenioType.INSS, false, Money.of("1"), 12))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
