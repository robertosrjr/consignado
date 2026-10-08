package br.com.consignado.platform.legacy.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/** Idempotencia do ledger (PBT-04): repetir chaves nunca cria averbacoes novas. */
class AverbacaoLedgerProperties {

  @Property
  void unique_count_equals_distinct_keys_and_duplicates_the_rest(
      @ForAll @Size(max = 400) List<@IntRange(min = 0, max = 30) Integer> picks) {
    UUID[] pool = new UUID[31];
    for (int i = 0; i < pool.length; i++) {
      pool[i] = new UUID(i, i * 31L);
    }
    AverbacaoLedger ledger = new AverbacaoLedger(1_000);
    Set<Integer> distinct = new HashSet<>();

    for (int pick : picks) {
      distinct.add(pick);
      ledger.register(pool[pick]);
    }

    assertThat(ledger.uniqueCount()).isEqualTo(distinct.size());
    assertThat(ledger.duplicateCount()).isEqualTo(picks.size() - distinct.size());
  }

  @Property
  void registering_twice_is_the_same_as_once(@ForAll long most, @ForAll long least) {
    AverbacaoLedger ledger = new AverbacaoLedger(10);
    UUID key = new UUID(most, least);

    UUID first = ledger.register(key).averbacaoId();
    UUID second = ledger.register(key).averbacaoId();

    assertThat(second).isEqualTo(first);
    assertThat(ledger.size()).isEqualTo(1);
  }
}
