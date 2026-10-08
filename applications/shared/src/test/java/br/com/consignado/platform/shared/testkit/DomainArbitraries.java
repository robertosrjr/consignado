package br.com.consignado.platform.shared.testkit;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.contract.ProposalMessage;
import br.com.consignado.platform.shared.payload.InboxPayload;
import br.com.consignado.platform.shared.vo.Cpf;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.shared.vo.TenantId;
import java.math.BigDecimal;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

/**
 * Geradores jqwik de dominio, reutilizaveis por todos os modulos via test-jar (PBT-07). Os
 * geradores respeitam as restricoes de negocio e incluem valores de borda.
 */
public final class DomainArbitraries {

  private static final String TENANT_CHARS =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-";
  private static final long MAX_CENTS = 999_999_999_999L;

  private DomainArbitraries() {}

  /** CPF valido (digitos verificadores corretos, nao repetidos). */
  public static Arbitrary<Cpf> validCpf() {
    return Arbitraries.integers()
        .between(0, 999_999_999)
        .map(DomainArbitraries::nineDigits)
        .filter(base -> !allSame(base))
        .map(
            base ->
                new Cpf(base + checkDigit(base, 9) + checkDigit(base + checkDigit(base, 9), 10)));
  }

  /**
   * Textos que nao sao CPF valido: tamanho errado, caracteres invalidos ou digitos verificadores
   * errados.
   */
  public static Arbitrary<String> invalidCpfText() {
    Arbitrary<String> wrongLength = Arbitraries.strings().numeric().ofMinLength(0).ofMaxLength(10);
    Arbitrary<String> tooLong = Arbitraries.strings().numeric().ofMinLength(12).ofMaxLength(20);
    Arbitrary<String> nonDigits = Arbitraries.strings().alpha().ofLength(11);
    Arbitrary<String> wrongCheckDigits =
        validCpf()
            .map(
                cpf -> {
                  String digits = cpf.digits();
                  char last = digits.charAt(10);
                  char altered = last == '9' ? '0' : (char) (last + 1);
                  return digits.substring(0, 10) + altered;
                });
    return Arbitraries.oneOf(wrongLength, tooLong, nonDigits, wrongCheckDigits);
  }

  public static Arbitrary<IdempotencyKey> idempotencyKey() {
    return Combinators.combine(Arbitraries.longs(), Arbitraries.longs())
        .as((most, least) -> new IdempotencyKey(new UUID(most, least)));
  }

  public static Arbitrary<TenantId> tenantId() {
    return Arbitraries.strings()
        .withChars(TENANT_CHARS.toCharArray())
        .ofMinLength(1)
        .ofMaxLength(TenantId.MAX_LENGTH)
        .map(TenantId::new);
  }

  /** Valores monetarios validos, com bordas (0,00 e valor maximo) com frequencia garantida. */
  public static Arbitrary<Money> money() {
    Arbitrary<Money> general =
        Arbitraries.longs()
            .between(0, MAX_CENTS)
            .map(cents -> new Money(BigDecimal.valueOf(cents, 2)));
    Arbitrary<Money> edges = Arbitraries.of(new Money(BigDecimal.ZERO), new Money(Money.MAX));
    return Arbitraries.frequencyOf(
        net.jqwik.api.Tuple.of(9, general), net.jqwik.api.Tuple.of(1, edges));
  }

  public static Arbitrary<ConvenioType> convenio() {
    return Arbitraries.of(ConvenioType.class);
  }

  public static Arbitrary<InboxPayload> inboxPayload() {
    return Combinators.combine(
            validCpf(),
            convenio(),
            Arbitraries.of(true, false),
            money(),
            Arbitraries.integers()
                .between(InboxPayload.MIN_INSTALLMENTS, InboxPayload.MAX_INSTALLMENTS))
        .as(InboxPayload::of);
  }

  public static Arbitrary<Lane> lane() {
    return Arbitraries.of(Lane.class);
  }

  public static Arbitrary<ProposalMessage> proposalMessage() {
    return Combinators.combine(
            idempotencyKey(),
            tenantId(),
            lane(),
            Arbitraries.integers()
                .between(ProposalMessage.MIN_PRIORITY_SCORE, ProposalMessage.MAX_PRIORITY_SCORE))
        .as(ProposalMessage::of);
  }

  private static String nineDigits(int value) {
    return String.format("%09d", value);
  }

  private static boolean allSame(String digits) {
    return digits.chars().distinct().count() == 1;
  }

  private static int checkDigit(String digits, int length) {
    int sum = 0;
    for (int i = 0; i < length; i++) {
      sum += (digits.charAt(i) - '0') * (length + 1 - i);
    }
    int remainder = (sum * 10) % 11;
    return remainder == 10 ? 0 : remainder;
  }
}
