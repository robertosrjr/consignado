package br.com.consignado.platform.ingestion.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.ingestion.infrastructure.adapter.out.support.SystemTracingIdAdapter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class SystemTracingIdAdapterTest {
  @Test
  @SuppressWarnings("try")
  void should_keep_acceptance_identity_unique_when_http_requests_share_a_trace() {
    try (var ignored = MDC.putCloseable("traceId", "0123456789abcdef0123456789abcdef")) {
      var adapter = new SystemTracingIdAdapter();
      String first = adapter.currentTracingId();
      String second = adapter.currentTracingId();
      assertThat(UUID.fromString(first).toString()).isEqualTo(first);
      assertThat(second).isNotEqualTo(first);
    }
  }
}
