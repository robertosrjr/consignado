package br.com.consignado.platform.ingestion.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.ingestion.application.port.out.InboxItem;
import br.com.consignado.platform.ingestion.infrastructure.adapter.out.crypto.EnvelopePayloadProtectionAdapter;
import br.com.consignado.platform.ingestion.infrastructure.adapter.out.persistence.InMemoryInboxAdapter;
import br.com.consignado.platform.ingestion.infrastructure.adapter.out.support.SystemTracingIdAdapter;
import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.crypto.LocalDataKeyProvider;
import br.com.consignado.platform.shared.crypto.PayloadEnvelope;
import br.com.consignado.platform.shared.payload.InboxPayload;
import br.com.consignado.platform.shared.payload.InboxPayloadCodec;
import br.com.consignado.platform.shared.vo.Cpf;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.shared.vo.TenantId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class SmallAdaptersTest {

  @Test
  void should_protect_the_payload_so_the_cpf_is_only_recoverable_with_the_key() {
    PayloadEnvelope envelope = new PayloadEnvelope(new LocalDataKeyProvider(new byte[32]));
    EnvelopePayloadProtectionAdapter adapter = new EnvelopePayloadProtectionAdapter(envelope);
    InboxPayload payload =
        InboxPayload.of(new Cpf("52998224725"), ConvenioType.INSS, false, Money.of("100"), 12);

    String protectedPayload = adapter.protect(payload);

    assertThat(protectedPayload).doesNotContain("52998224725");
    assertThat(InboxPayloadCodec.fromBytes(envelope.unprotect(protectedPayload)))
        .isEqualTo(payload);
  }

  @Test
  void should_apply_conditional_write_semantics_in_the_in_memory_inbox() {
    InMemoryInboxAdapter inbox = new InMemoryInboxAdapter();
    InboxItem item =
        new InboxItem(
            new IdempotencyKey(UUID.randomUUID()),
            new TenantId("banco-a"),
            ConvenioType.INSS,
            false,
            Money.of("1"),
            "p",
            1L,
            2L,
            "trace-1");

    assertThat(inbox.putIfAbsent(item).created()).isTrue();
    assertThat(inbox.putIfAbsent(item).created()).isFalse();
    assertThat(inbox.putIfAbsent(item).existingTracingId()).isEqualTo("trace-1");
    assertThat(inbox.size()).isEqualTo(1);
  }

  @Test
  void should_generate_a_uuid_tracing_id_when_there_is_no_trace_in_the_mdc() {
    MDC.remove("traceId");

    assertThat(UUID.fromString(new SystemTracingIdAdapter().currentTracingId())).isNotNull();
  }

  @Test
  void should_reuse_the_mdc_trace_id_formatted_as_a_uuid() {
    MDC.put("traceId", "0123456789abcdef0123456789abcdef");
    try {
      assertThat(new SystemTracingIdAdapter().currentTracingId())
          .isEqualTo("01234567-89ab-cdef-0123-456789abcdef");
    } finally {
      MDC.remove("traceId");
    }
  }

  @Test
  void should_ignore_a_malformed_mdc_trace_id() {
    MDC.put("traceId", "nao-e-um-trace");
    try {
      assertThat(new SystemTracingIdAdapter().currentTracingId()).hasSize(36).contains("-");
    } finally {
      MDC.remove("traceId");
    }
  }
}
