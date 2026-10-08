package br.com.consignado.platform.shared.payload;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.contract.MessageContractException;
import br.com.consignado.platform.shared.vo.Cpf;
import br.com.consignado.platform.shared.vo.Money;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Serializacao JSON do {@link InboxPayload} (formato plano, sem tipagem polimorfica). */
public final class InboxPayloadCodec {

  private static final JsonMapper MAPPER =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private InboxPayloadCodec() {}

  private record Wire(
      int schemaVersion,
      String cpf,
      String convenio,
      boolean portability,
      String amount,
      int installments) {}

  public static byte[] toBytes(InboxPayload payload) {
    Wire wire =
        new Wire(
            payload.schemaVersion(),
            payload.cpf().digits(),
            payload.convenio().name(),
            payload.portability(),
            payload.amount().amount().toPlainString(),
            payload.installments());
    try {
      return MAPPER.writeValueAsString(wire).getBytes(StandardCharsets.UTF_8);
    } catch (RuntimeException e) {
      throw new MessageContractException("Falha ao serializar payload de proposta", e);
    }
  }

  public static InboxPayload fromBytes(byte[] bytes) {
    try {
      Wire wire = MAPPER.readValue(new String(bytes, StandardCharsets.UTF_8), Wire.class);
      return new InboxPayload(
          wire.schemaVersion(),
          new Cpf(wire.cpf()),
          ConvenioType.valueOf(wire.convenio()),
          wire.portability(),
          Money.of(wire.amount()),
          wire.installments());
    } catch (RuntimeException e) {
      throw new MessageContractException("Payload de proposta invalido", e);
    }
  }
}
