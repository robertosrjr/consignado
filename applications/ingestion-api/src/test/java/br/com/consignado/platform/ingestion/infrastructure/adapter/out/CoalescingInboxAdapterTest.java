package br.com.consignado.platform.ingestion.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.ingestion.application.port.out.DurableInboxPort;
import br.com.consignado.platform.ingestion.application.port.out.InboxItem;
import br.com.consignado.platform.ingestion.application.port.out.InboxWriteResult;
import br.com.consignado.platform.ingestion.domain.InboxUnavailableException;
import br.com.consignado.platform.ingestion.infrastructure.adapter.out.persistence.CoalescingInboxAdapter;
import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.shared.vo.TenantId;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CoalescingInboxAdapterTest {

  private static final String KEY = "e3b0c442-98fc-11ee-b9d1-0242ac120002";
  private static final Duration TTL = Duration.ofSeconds(60);

  private final AtomicLong nanos = new AtomicLong();
  private final AtomicInteger writes = new AtomicInteger();

  private static InboxItem item(String tenant, String key, String tracingId) {
    return new InboxItem(
        IdempotencyKey.parse(key),
        new TenantId(tenant),
        ConvenioType.INSS,
        false,
        Money.of("1000.00"),
        "protected",
        1_000L,
        2_000L,
        tracingId);
  }

  private CoalescingInboxAdapter adapter(DurableInboxPort delegate, int maxEntries) {
    return new CoalescingInboxAdapter(delegate, TTL, Duration.ofSeconds(2), maxEntries, nanos::get);
  }

  private DurableInboxPort counting() {
    return item -> {
      writes.incrementAndGet();
      return InboxWriteResult.createdNew();
    };
  }

  @Test
  void should_answer_repetitions_within_the_ttl_as_duplicate_without_touching_the_store() {
    CoalescingInboxAdapter adapter = adapter(counting(), 10);

    InboxWriteResult first = adapter.putIfAbsent(item("banco-a", KEY, "trace-1"));
    InboxWriteResult second = adapter.putIfAbsent(item("banco-a", KEY, "trace-2"));

    assertThat(first.created()).isTrue();
    assertThat(second.created()).isFalse();
    assertThat(second.existingTracingId()).isEqualTo("trace-1");
    assertThat(second.existingCreatedAt()).isEqualTo(1_000L);
    assertThat(writes).hasValue(1);
  }

  @Test
  void should_go_back_to_the_store_after_the_ttl_expires() {
    CoalescingInboxAdapter adapter = adapter(counting(), 10);

    adapter.putIfAbsent(item("banco-a", KEY, "trace-1"));
    nanos.addAndGet(TTL.toNanos());
    adapter.putIfAbsent(item("banco-a", KEY, "trace-2"));

    assertThat(writes).hasValue(2);
  }

  @Test
  void should_never_share_entries_between_tenants() {
    CoalescingInboxAdapter adapter = adapter(counting(), 10);

    adapter.putIfAbsent(item("banco-a", KEY, "trace-1"));
    InboxWriteResult other = adapter.putIfAbsent(item("banco-b", KEY, "trace-2"));

    assertThat(other.created()).isTrue();
    assertThat(writes).hasValue(2);
  }

  @Test
  void should_keep_the_store_answer_when_the_key_already_existed_there() {
    CoalescingInboxAdapter adapter =
        adapter(item -> InboxWriteResult.alreadyExists("trace-original", 500L), 10);

    adapter.putIfAbsent(item("banco-a", KEY, "trace-1"));
    InboxWriteResult again = adapter.putIfAbsent(item("banco-a", KEY, "trace-2"));

    assertThat(again.existingTracingId()).isEqualTo("trace-original");
    assertThat(again.existingCreatedAt()).isEqualTo(500L);
  }

  @Test
  void should_not_remember_a_failure_so_the_next_call_retries() {
    AtomicInteger calls = new AtomicInteger();
    CoalescingInboxAdapter adapter =
        adapter(
            item -> {
              if (calls.incrementAndGet() == 1) {
                throw new InboxUnavailableException("fora", new RuntimeException());
              }
              return InboxWriteResult.createdNew();
            },
            10);

    assertThatThrownBy(() -> adapter.putIfAbsent(item("banco-a", KEY, "trace-1")))
        .isInstanceOf(InboxUnavailableException.class);
    InboxWriteResult retry = adapter.putIfAbsent(item("banco-a", KEY, "trace-2"));

    assertThat(retry.created()).isTrue();
    assertThat(calls).hasValue(2);
  }

  @Test
  void should_bypass_the_cache_when_full_and_nothing_has_expired() {
    CoalescingInboxAdapter adapter = adapter(counting(), 1);

    adapter.putIfAbsent(item("banco-a", KEY, "trace-1"));
    String otherKey = "f3b0c442-98fc-11ee-b9d1-0242ac120002";
    adapter.putIfAbsent(item("banco-a", otherKey, "trace-2"));
    adapter.putIfAbsent(item("banco-a", otherKey, "trace-3"));

    assertThat(writes).hasValue(3);
  }

  @Test
  void should_evict_expired_entries_to_make_room() {
    CoalescingInboxAdapter adapter = adapter(counting(), 1);

    adapter.putIfAbsent(item("banco-a", KEY, "trace-1"));
    nanos.addAndGet(TTL.toNanos());
    String otherKey = "f3b0c442-98fc-11ee-b9d1-0242ac120002";
    adapter.putIfAbsent(item("banco-a", otherKey, "trace-2"));
    InboxWriteResult cached = adapter.putIfAbsent(item("banco-a", otherKey, "trace-3"));

    assertThat(cached.created()).isFalse();
    assertThat(writes).hasValue(2);
  }

  @Test
  void should_make_concurrent_requests_wait_for_the_write_in_progress() throws Exception {
    CountDownLatch writing = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CoalescingInboxAdapter adapter =
        adapter(
            item -> {
              writes.incrementAndGet();
              writing.countDown();
              await(release);
              return InboxWriteResult.createdNew();
            },
            10);

    CompletableFuture<InboxWriteResult> first =
        CompletableFuture.supplyAsync(() -> adapter.putIfAbsent(item("banco-a", KEY, "trace-1")));
    assertThat(writing.await(5, TimeUnit.SECONDS)).isTrue();
    CompletableFuture<InboxWriteResult> waiter =
        CompletableFuture.supplyAsync(() -> adapter.putIfAbsent(item("banco-a", KEY, "trace-2")));
    release.countDown();

    assertThat(first.get(5, TimeUnit.SECONDS).created()).isTrue();
    assertThat(waiter.get(5, TimeUnit.SECONDS).existingTracingId()).isEqualTo("trace-1");
    assertThat(writes).hasValue(1);
  }

  @Test
  void should_report_unavailable_when_the_write_in_progress_takes_too_long() throws Exception {
    CountDownLatch writing = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CoalescingInboxAdapter adapter =
        new CoalescingInboxAdapter(
            item -> {
              writing.countDown();
              await(release);
              return InboxWriteResult.createdNew();
            },
            TTL,
            Duration.ofMillis(50),
            10,
            nanos::get);

    CompletableFuture<InboxWriteResult> first =
        CompletableFuture.supplyAsync(() -> adapter.putIfAbsent(item("banco-a", KEY, "trace-1")));
    assertThat(writing.await(5, TimeUnit.SECONDS)).isTrue();

    assertThatThrownBy(() -> adapter.putIfAbsent(item("banco-a", KEY, "trace-2")))
        .isInstanceOf(InboxUnavailableException.class);
    release.countDown();
    first.get(5, TimeUnit.SECONDS);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
