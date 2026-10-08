package br.com.consignado.platform.shared.contract;

import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;

/**
 * Contrato Dispatcher -> Worker. Leva apenas a referencia (chave) e metadados de roteamento; nunca
 * carrega CPF nem payload. O Worker le o item completo no DurableInbox pela chave.
 */
public record ProposalMessage(
    int schemaVersion, IdempotencyKey key, TenantId tenant, Lane lane, int priorityScore) {

  public static final int CURRENT_SCHEMA_VERSION = 1;
  public static final int MIN_PRIORITY_SCORE = 0;
  public static final int MAX_PRIORITY_SCORE = 100;

  public ProposalMessage {
    if (schemaVersion != CURRENT_SCHEMA_VERSION) {
      throw new IllegalArgumentException("Versao de esquema nao suportada");
    }
    if (key == null || tenant == null || lane == null) {
      throw new IllegalArgumentException("Mensagem de proposta invalida");
    }
    if (priorityScore < MIN_PRIORITY_SCORE || priorityScore > MAX_PRIORITY_SCORE) {
      throw new IllegalArgumentException("Mensagem de proposta invalida");
    }
  }

  public static ProposalMessage of(
      IdempotencyKey key, TenantId tenant, Lane lane, int priorityScore) {
    return new ProposalMessage(CURRENT_SCHEMA_VERSION, key, tenant, lane, priorityScore);
  }
}
