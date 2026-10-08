package br.com.consignado.platform.legacy.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Pedido de averbacao. O CPF nunca e registrado em log. */
public record AverbacaoRequest(
    @NotBlank @Pattern(regexp = "\\d{11}") String cpf,
    @NotBlank @Size(max = 20) String convenio,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
    @Min(1) @Max(120) int installments) {

  @Override
  public String toString() {
    return "AverbacaoRequest[convenio=" + convenio + ", installments=" + installments + "]";
  }
}
