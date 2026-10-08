package br.com.consignado.platform.starter;

import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.json.JsonWriter.ValueProcessor;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Rede de protecao da telemetria: mascara dados pessoais em todo valor de texto do log estruturado
 * (mensagem, campos do MDC, pares chave-valor e a stack trace), antes de o JSON ir para stdout.
 *
 * <p>Vale para qualquer excecao de biblioteca cuja mensagem carregue CPF, cartao, telefone ou
 * e-mail. Registrada por {@link PlatformDefaultsEnvironmentPostProcessor} em {@code
 * logging.structured.json.customizer}.
 */
public final class PiiRedactingJsonMembersCustomizer<T>
    implements StructuredLoggingJsonMembersCustomizer<T> {

  @Override
  public void customize(JsonWriter.Members<T> members) {
    members.applyingValueProcessor(ValueProcessor.of(String.class, LogSanitizer::sanitize));
  }
}
