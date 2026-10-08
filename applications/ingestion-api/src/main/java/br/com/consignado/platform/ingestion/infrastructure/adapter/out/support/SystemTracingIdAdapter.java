package br.com.consignado.platform.ingestion.infrastructure.adapter.out.support;

import br.com.consignado.platform.ingestion.application.port.out.TracingIdPort;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Identificador de rastreio devolvido ao parceiro: reaproveita o traceId do MDC (quando o tracing
 * esta ativo, 32 hex) formatado como UUID; senao gera um UUID aleatorio.
 */
public class SystemTracingIdAdapter implements TracingIdPort {

  private static final Pattern TRACE_ID = Pattern.compile("^[0-9a-f]{32}$");

  @Override
  public String currentTracingId() {
    String traceId = MDC.get("traceId");
    if (traceId != null && TRACE_ID.matcher(traceId).matches()) {
      return traceId.substring(0, 8)
          + "-"
          + traceId.substring(8, 12)
          + "-"
          + traceId.substring(12, 16)
          + "-"
          + traceId.substring(16, 20)
          + "-"
          + traceId.substring(20);
    }
    return UUID.randomUUID().toString();
  }
}
