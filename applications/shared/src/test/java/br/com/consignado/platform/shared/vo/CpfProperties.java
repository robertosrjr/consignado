package br.com.consignado.platform.shared.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.shared.testkit.DomainArbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Propriedades de {@link Cpf} (PBT-03 invariantes, PBT-02 round-trip de normalizacao). */
class CpfProperties {

  @Provide
  Arbitrary<Cpf> validCpfs() {
    return DomainArbitraries.validCpf();
  }

  @Provide
  Arbitrary<String> invalidCpfs() {
    return DomainArbitraries.invalidCpfText();
  }

  @Property
  void valid_cpf_is_always_accepted_and_keeps_its_digits(@ForAll("validCpfs") Cpf cpf) {
    assertThat(new Cpf(cpf.digits())).isEqualTo(cpf);
  }

  @Property
  void formatted_cpf_round_trips_to_the_same_value(@ForAll("validCpfs") Cpf cpf) {
    String d = cpf.digits();
    String formatted =
        d.substring(0, 3)
            + "."
            + d.substring(3, 6)
            + "."
            + d.substring(6, 9)
            + "-"
            + d.substring(9);

    assertThat(Cpf.of(formatted)).isEqualTo(cpf);
  }

  @Property
  void to_string_never_reveals_more_than_the_last_two_digits(@ForAll("validCpfs") Cpf cpf) {
    String masked = cpf.toString();

    assertThat(masked).endsWith(cpf.digits().substring(9));
    assertThat(masked).doesNotContain(cpf.digits().substring(0, 9));
  }

  @Property
  void invalid_text_is_always_rejected(@ForAll("invalidCpfs") String text) {
    assertThatThrownBy(() -> Cpf.of(text)).isInstanceOf(IllegalArgumentException.class);
  }
}
