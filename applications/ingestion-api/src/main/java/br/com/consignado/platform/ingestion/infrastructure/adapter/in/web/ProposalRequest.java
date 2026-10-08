package br.com.consignado.platform.ingestion.infrastructure.adapter.in.web;

import br.com.consignado.platform.shared.contract.ConvenioType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** Corpo de {@code POST /v1/proposals}. O CPF nunca e impresso (toString sem PII). */
@Schema(description = "Proposta de credito consignado")
public record ProposalRequest(
    @Schema(
            description = "CPF do cliente (11 digitos, com ou sem pontuacao)",
            example = "52998224725")
        @NotBlank
        @Pattern(regexp = "[0-9.\\-]{11,14}")
        String customerCpf,
    @Schema(description = "Convenio", example = "INSS") @NotNull ConvenioType convenioCode,
    @Schema(description = "Portabilidade via Open Finance", example = "false")
        Boolean isPortability,
    @Schema(description = "Valor da proposta em BRL", example = "15000.50")
        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 10, fraction = 2)
        BigDecimal amount,
    @Schema(description = "Numero de parcelas (1 a 120)", example = "84") @NotNull @Min(1) @Max(120)
        Integer installments) {

  @Override
  public String toString() {
    return "ProposalRequest[convenioCode=" + convenioCode + ", installments=" + installments + "]";
  }
}
