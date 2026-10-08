package br.com.consignado.platform.starter;

import java.util.Objects;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mascara dados pessoais em texto destinado a telemetria (LGPD, Lei 13.709/2018, art. 6o III e
 * VII): CPF, cartao, telefone e e-mail. Implementacao unica do projeto; a tabela de formatos esta
 * em {@code .claude/skills/lgpd-sre-compliance-skill}.
 *
 * <p>E uma rede de protecao, nao a regra principal: dado pessoal nao deve chegar ao logger. Nunca
 * devolve {@code null}.
 */
public final class LogSanitizer {

  // A ordem importa: cartao antes de CPF, para que 16 digitos nao sejam lidos como CPF.
  private static final Pattern CARD =
      Pattern.compile("\\b(\\d{4})[- ]?\\d{4}[- ]?\\d{4}[- ]?(\\d{4})\\b");
  private static final Pattern CPF =
      Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?(\\d{3})-?(\\d{2})\\b");
  private static final Pattern PHONE = Pattern.compile("\\(\\d{2}\\)\\s?\\d{4,5}-(\\d{4})");
  private static final Pattern EMAIL =
      Pattern.compile(
          "\\b([A-Za-z0-9])(?:[A-Za-z0-9._%+-]*([A-Za-z0-9]))?(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b");

  private LogSanitizer() {}

  /** Devolve o texto com os dados pessoais conhecidos mascarados. */
  public static String sanitize(String message) {
    if (message == null) {
      return "";
    }
    String masked = CARD.matcher(message).replaceAll("$1-XXXX-XXXX-$2");
    masked = CPF.matcher(masked).replaceAll("***.***.$1-$2");
    masked = PHONE.matcher(masked).replaceAll("(**) *****-$1");
    return EMAIL.matcher(masked).replaceAll(LogSanitizer::maskEmail);
  }

  private static String maskEmail(MatchResult m) {
    String masked = m.group(1) + "***" + Objects.toString(m.group(2), "") + m.group(3);
    return Matcher.quoteReplacement(masked);
  }
}
