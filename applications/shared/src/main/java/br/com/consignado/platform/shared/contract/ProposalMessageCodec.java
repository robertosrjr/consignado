package br.com.consignado.platform.shared.contract;

import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serializacao JSON do {@link ProposalMessage}, usada por Dispatcher e Worker. Formato plano e
 * explicito (sem tipagem polimorfica). Campos desconhecidos sao ignorados (compatibilidade para
 * frente); versao de esquema desconhecida e rejeitada.
 */
public final class ProposalMessageCodec {

  private static final JsonMapper MAPPER =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private ProposalMessageCodec() {}

  /** Representacao de fio: tipos primitivos, independente dos value objects. */
  private record Wire(
      int schemaVersion, String key, String tenant, String lane, int priorityScore) {}

  public static String toJson(ProposalMessage message) {
    Wire wire =
        new Wire(
            message.schemaVersion(),
            message.key().toString(),
            message.tenant().value(),
            message.lane().name(),
            message.priorityScore());
    try {
      return MAPPER.writeValueAsString(wire);
    } catch (RuntimeException e) {
      throw new MessageContractException("Falha ao serializar mensagem de proposta", e);
    }
  }

  public static ProposalMessage fromJson(String json) {
    try {
      Wire wire = MAPPER.readValue(json, Wire.class);
      return new ProposalMessage(
          wire.schemaVersion(),
          IdempotencyKey.parse(wire.key()),
          new TenantId(wire.tenant()),
          Lane.valueOf(wire.lane()),
          wire.priorityScore());
    } catch (RuntimeException e) {
      throw new MessageContractException("Mensagem de proposta invalida", e);
    }
  }
}
