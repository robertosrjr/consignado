package br.com.consignado.platform.shared.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

  private static final String CANONICAL = "e3b0c442-98fc-11ee-b9d1-0242ac120002";

  @Test
  void should_parse_canonical_uuid() {
    assertThat(IdempotencyKey.parse(CANONICAL).value()).isEqualTo(UUID.fromString(CANONICAL));
  }

  @Test
  void should_render_lowercase_canonical_form() {
    assertThat(IdempotencyKey.parse(CANONICAL.toUpperCase()).toString()).isEqualTo(CANONICAL);
  }

  @Test
  void should_reject_lenient_uuid_formats_that_uuid_from_string_would_accept() {
    assertThatThrownBy(() -> IdempotencyKey.parse("1-1-1-1-1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_null_blank_and_garbage() {
    assertThatThrownBy(() -> IdempotencyKey.parse(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> IdempotencyKey.parse(" "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> IdempotencyKey.parse("not-a-uuid"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new IdempotencyKey(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
