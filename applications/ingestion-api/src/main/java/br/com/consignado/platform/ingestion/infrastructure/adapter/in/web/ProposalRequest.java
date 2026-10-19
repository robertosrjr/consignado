package br.com.consignado.platform.ingestion.infrastructure.adapter.in.web;

import br.com.consignado.platform.shared.contract.ConvenioType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
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
        BigDecimal amount,
    @Schema(description = "Numero de parcelas (1 a 120)", example = "84") @NotNull @Min(1) @Max(120)
        Integer installments,
    @Schema(description = "Data contratual ISO; quando ausente, usa a data UTC do recebimento")
        java.time.LocalDate contractDate) {

  @com.fasterxml.jackson.annotation.JsonCreator
  public static ProposalRequest fromJson(
      @com.fasterxml.jackson.annotation.JsonProperty("customerCpf") String cpf,
      @com.fasterxml.jackson.annotation.JsonProperty("convenioCode") String convenio,
      @com.fasterxml.jackson.annotation.JsonProperty("isPortability") Boolean portability,
      @com.fasterxml.jackson.annotation.JsonProperty("amount") BigDecimal amount,
      @com.fasterxml.jackson.annotation.JsonProperty("installments") Integer installments,
      @com.fasterxml.jackson.annotation.JsonProperty("contractDate")
          java.time.LocalDate contractDate) {
    return new ProposalRequest(
        cpf,
        convenio == null
            ? null
            : ConvenioType.valueOf(convenio.trim().toUpperCase(java.util.Locale.ROOT)),
        portability,
        amount,
        installments,
        contractDate);
  }

  @Override
  public String toString() {
    return "ProposalRequest[convenioCode=" + convenioCode + ", installments=" + installments + "]";
  }
}
