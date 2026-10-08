package br.com.consignado.platform.ingestion.application;

import br.com.consignado.platform.ingestion.application.port.out.ClockPort;
import br.com.consignado.platform.ingestion.application.port.out.DurableInboxPort;
import br.com.consignado.platform.ingestion.application.port.out.InboxItem;
import br.com.consignado.platform.ingestion.application.port.out.InboxWriteResult;
import br.com.consignado.platform.ingestion.application.port.out.PayloadProtectionPort;
import br.com.consignado.platform.ingestion.application.port.out.TenantQuotaPort;
import br.com.consignado.platform.ingestion.application.port.out.TracingIdPort;
import br.com.consignado.platform.shared.payload.InboxPayload;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Fakes simples das portas de saida, usados nos testes do caso de uso. */
final class Fakes {

  private Fakes() {}

  /** Inbox com a mesma semantica da escrita condicional. */
  static final class FakeInbox implements DurableInboxPort {
    final Map<IdempotencyKey, InboxItem> items = new LinkedHashMap<>();
    final List<InboxItem> writeAttempts = new ArrayList<>();
    RuntimeException failure;

    @Override
    public synchronized InboxWriteResult putIfAbsent(InboxItem item) {
      writeAttempts.add(item);
      if (failure != null) {
        throw failure;
      }
      InboxItem existing = items.putIfAbsent(item.idempotencyKey(), item);
      if (existing == null) {
        return InboxWriteResult.createdNew();
      }
      return InboxWriteResult.alreadyExists(existing.tracingId(), existing.createdAtEpochMillis());
    }
  }

  static final class FakeQuota implements TenantQuotaPort {
    boolean allow = true;
    final List<TenantId> asked = new ArrayList<>();

    @Override
    public boolean tryAcquire(TenantId tenant) {
      asked.add(tenant);
      return allow;
    }
  }

  static final class FakeProtection implements PayloadProtectionPort {
    final AtomicInteger calls = new AtomicInteger();

    @Override
    public String protect(InboxPayload payload) {
      calls.incrementAndGet();
      return "protected-" + calls.get();
    }
  }

  static final class FixedClock implements ClockPort {
    Instant now = Instant.parse("2026-10-08T12:00:00Z");

    @Override
    public Instant now() {
      return now;
    }
  }

  static final class SequenceTracing implements TracingIdPort {
    private final AtomicInteger sequence = new AtomicInteger();

    @Override
    public String currentTracingId() {
      return "trace-" + sequence.incrementAndGet();
    }
  }
}
