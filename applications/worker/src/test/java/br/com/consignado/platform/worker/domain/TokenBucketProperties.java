package br.com.consignado.platform.worker.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;

/**
 * Invariante do balde (PBT-03): em qualquer janela de 1 s, as permissoes concedidas nao excedem
 * {@code capacidade + taxa}; e o argumento que sustenta o teto de 180 TPS.
 */
class TokenBucketProperties {

  private static final long SECOND = 1_000_000_000L;

  @Property
  void grants_in_any_one_second_window_never_exceed_capacity_plus_rate(
      @ForAll @IntRange(min = 1, max = 400) int rate,
      @ForAll @IntRange(min = 1, max = 5) int capacity,
      @ForAll @Size(max = 1500) List<@LongRange(min = 0, max = 30_000_000L) Long> gapsNanos) {
    long[] clock = {100 * SECOND};
    TokenBucket bucket = new TokenBucket(rate, capacity, () -> clock[0]);
    List<Long> grants = new ArrayList<>();

    for (long gap : gapsNanos) {
      clock[0] += gap;
      if (bucket.tryConsume()) {
        grants.add(clock[0]);
      }
    }

    for (long end : grants) {
      long inWindow = grants.stream().filter(t -> t <= end && end - t < SECOND).count();
      assertThat(inWindow).isLessThanOrEqualTo((long) Math.floor(capacity + rate));
    }
  }

  @Property
  void tokens_are_always_within_zero_and_capacity(
      @ForAll @IntRange(min = 1, max = 400) int rate,
      @ForAll @IntRange(min = 1, max = 5) int capacity,
      @ForAll @Size(max = 300) List<@LongRange(min = 0, max = 50_000_000L) Long> gapsNanos) {
    long[] clock = {5 * SECOND};
    TokenBucket bucket = new TokenBucket(rate, capacity, () -> clock[0]);

    for (long gap : gapsNanos) {
      clock[0] += gap;
      bucket.tryConsume();
      assertThat(bucket.tokens()).isBetween(0.0, (double) capacity);
    }
  }
}
