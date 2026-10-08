package br.com.consignado.platform.legacy.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AverbacaoLedgerTest {

  @Test
  void should_create_on_first_call_and_replay_the_same_id_afterwards() {
    AverbacaoLedger ledger = new AverbacaoLedger(100);
    UUID key = UUID.randomUUID();

    AverbacaoLedger.Registration first = ledger.register(key);
    AverbacaoLedger.Registration second = ledger.register(key);

    assertThat(first.duplicate()).isFalse();
    assertThat(second.duplicate()).isTrue();
    assertThat(second.averbacaoId()).isEqualTo(first.averbacaoId());
    assertThat(ledger.uniqueCount()).isEqualTo(1);
    assertThat(ledger.duplicateCount()).isEqualTo(1);
  }

  @Test
  void should_derive_a_stable_id_from_the_key() {
    UUID key = UUID.fromString("e3b0c442-98fc-11ee-b9d1-0242ac120002");

    assertThat(new AverbacaoLedger(10).register(key).averbacaoId())
        .isEqualTo(new AverbacaoLedger(10).register(key).averbacaoId());
  }

  @Test
  void should_forget_the_oldest_keys_when_the_limit_is_exceeded() {
    AverbacaoLedger ledger = new AverbacaoLedger(2);
    UUID oldest = UUID.randomUUID();
    ledger.register(oldest);
    ledger.register(UUID.randomUUID());
    ledger.register(UUID.randomUUID());

    assertThat(ledger.size()).isEqualTo(2);
    assertThat(ledger.register(oldest).duplicate()).isFalse();
  }

  @Test
  void should_reject_invalid_size() {
    assertThatThrownBy(() -> new AverbacaoLedger(0)).isInstanceOf(IllegalArgumentException.class);
  }
}
