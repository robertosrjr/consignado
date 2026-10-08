package br.com.consignado.platform.dispatcher.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DispatcherConfigTest {

  private static final Map<String, String> MINIMAL =
      Map.of(
          DispatcherConfig.FAST_QUEUE_URL, "http://q/fast",
          DispatcherConfig.STANDARD_QUEUE_URL, "http://q/standard");

  @Test
  void should_apply_defaults_when_only_the_queue_urls_are_set() {
    DispatcherConfig config = DispatcherConfig.fromEnvironment(MINIMAL);

    assertThat(config.fastQueueUrl()).isEqualTo("http://q/fast");
    assertThat(config.standardQueueUrl()).isEqualTo("http://q/standard");
    assertThat(config.inboxTable()).isEqualTo("DurableInbox");
    assertThat(config.rules().vipTenants()).isEmpty();
    assertThat(config.rules().amountThreshold().amount()).isEqualByComparingTo("50000.00");
    assertThat(config.rules().fastThreshold()).isEqualTo(30);
  }

  @Test
  void should_parse_vip_tenants_threshold_and_table_from_the_environment() {
    DispatcherConfig config =
        DispatcherConfig.fromEnvironment(
            Map.of(
                DispatcherConfig.FAST_QUEUE_URL, "f",
                DispatcherConfig.STANDARD_QUEUE_URL, "s",
                DispatcherConfig.INBOX_TABLE, "MinhaTabela",
                DispatcherConfig.VIP_TENANTS, " banco-a , banco-b ,, ",
                DispatcherConfig.AMOUNT_THRESHOLD, "75000.50",
                DispatcherConfig.FAST_THRESHOLD, "40"));

    assertThat(config.inboxTable()).isEqualTo("MinhaTabela");
    assertThat(config.rules().vipTenants()).containsExactlyInAnyOrder("banco-a", "banco-b");
    assertThat(config.rules().amountThreshold().amount()).isEqualByComparingTo("75000.50");
    assertThat(config.rules().fastThreshold()).isEqualTo(40);
  }

  @Test
  void should_fail_when_a_queue_url_is_missing() {
    assertThatThrownBy(
            () -> DispatcherConfig.fromEnvironment(Map.of(DispatcherConfig.FAST_QUEUE_URL, "f")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(DispatcherConfig.STANDARD_QUEUE_URL);
    assertThatThrownBy(() -> DispatcherConfig.fromEnvironment(Map.of()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void should_fail_on_invalid_numeric_settings() {
    assertThatThrownBy(
            () ->
                DispatcherConfig.fromEnvironment(
                    Map.of(
                        DispatcherConfig.FAST_QUEUE_URL, "f",
                        DispatcherConfig.STANDARD_QUEUE_URL, "s",
                        DispatcherConfig.AMOUNT_THRESHOLD, "abc")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
