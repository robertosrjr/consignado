package br.com.consignado.platform.shared.payload;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.vo.Cpf;
import br.com.consignado.platform.shared.vo.Money;

/**
 * Dados sensiveis da proposta guardados cifrados no DurableInbox (inclui o CPF). {@link
 * #toString()} usa o CPF mascarado.
 */
public record InboxPayload(
    int schemaVersion,
    Cpf cpf,
    ConvenioType convenio,
    boolean portability,
    Money amount,
    int installments) {

  public static final int CURRENT_SCHEMA_VERSION = 1;
  public static final int MIN_INSTALLMENTS = 1;
  public static final int MAX_INSTALLMENTS = 120;

  public InboxPayload {
    if (schemaVersion != CURRENT_SCHEMA_VERSION) {
      throw new IllegalArgumentException("Versao de esquema nao suportada");
    }
    if (cpf == null || convenio == null || amount == null) {
      throw new IllegalArgumentException("Payload de proposta invalido");
    }
    if (installments < MIN_INSTALLMENTS || installments > MAX_INSTALLMENTS) {
      throw new IllegalArgumentException("Numero de parcelas invalido");
    }
  }

  public static InboxPayload of(
      Cpf cpf, ConvenioType convenio, boolean portability, Money amount, int installments) {
    return new InboxPayload(
        CURRENT_SCHEMA_VERSION, cpf, convenio, portability, amount, installments);
  }
}
