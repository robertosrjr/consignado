package br.com.consignado.platform.worker.application.port.out;

import br.com.consignado.platform.worker.domain.LegacyResult;

/** Porta de saida: chamada ao Core Banking legado, protegida por circuit breaker no adaptador. */
public interface LegacyCorePort {

  /** Nunca lanca por falha do legado: traduz tudo em {@link LegacyResult}. */
  LegacyResult submit(LegacySubmission submission);
}
