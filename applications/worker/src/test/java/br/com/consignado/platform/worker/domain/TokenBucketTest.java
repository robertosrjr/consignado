package br.com.consignado.platform.worker.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

  private static final long SECOND = 1_000_000_000L;

  private final AtomicLong now = new AtomicLong(1_000 * SECOND);

  @Test
  void should_start_full_and_grant_up_to_the_capacity_at_once() {
    TokenBucket bucket = new TokenBucket(10, 2, now::get);

    assertThat(bucket.tryConsume()).isTrue();
    assertThat(bucket.tryConsume()).isTrue();
    assertThat(bucket.tryConsume()).isFalse();
  }

  @Test
  void should_refill_at_the_configured_rate() {
    TokenBucket bucket = new TokenBucket(10, 2, now::get);
    bucket.tryConsume();
    bucket.tryConsume();

    now.addAndGet(SECOND / 10 - 1);
    assertThat(bucket.tryConsume()).isFalse();
    now.addAndGet(1);
    assertThat(bucket.tryConsume()).isTrue();
  }

  @Test
  void should_never_accumulate_more_than_the_capacity() {
    TokenBucket bucket = new TokenBucket(100, 2, now::get);

    now.addAndGet(60 * SECOND);

    assertThat(bucket.tokens()).isEqualTo(2.0);
  }

  @Test
  void should_report_fractional_tokens() {
    TokenBucket bucket = new TokenBucket(10, 2, now::get);
    bucket.tryConsume();
    bucket.tryConsume();

    now.addAndGet(SECOND / 20);

    assertThat(bucket.tokens()).isBetween(0.49, 0.51);
  }

  @Test
  void should_reject_invalid_configuration() {
    assertThatThrownBy(() -> new TokenBucket(0, 2, now::get))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TokenBucket(10, 0.5, now::get))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
