package br.com.consignado.platform.ingestion.infrastructure.adapter.in.web;

import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalCommand;
import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalResult;
import br.com.consignado.platform.ingestion.application.port.in.AcceptProposalUseCase;
import br.com.consignado.platform.ingestion.infrastructure.config.IngestionProperties;
import br.com.consignado.platform.shared.contract.HeaderNames;
import br.com.consignado.platform.shared.vo.Cpf;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.shared.vo.TenantId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Adaptador de entrada HTTP: traduz o pedido para o caso de uso e o resultado para 202. */
@RestController
@RequestMapping("/v1/proposals")
@Tag(name = "Proposals", description = "Ingestao duravel de propostas de credito consignado")
class ProposalController {

  private final AcceptProposalUseCase useCase;
  private final String tenantClaim;

  ProposalController(AcceptProposalUseCase useCase, IngestionProperties properties) {
    this.useCase = useCase;
    this.tenantClaim = properties.jwt().tenantClaim();
  }

  @PostMapping
  @Operation(
      summary = "Ingestao duravel de proposta",
      description =
          "Grava a proposta de forma idempotente e responde 202. Repeticoes com a mesma"
              + " Idempotency-Key respondem 202 DUPLICATE_ACCEPTED sem reprocessar.")
  @ApiResponse(
      responseCode = "202",
      description = "Proposta aceita (RECEIVED ou DUPLICATE_ACCEPTED)")
  @ApiResponse(responseCode = "400", description = "Payload ou headers invalidos")
  @ApiResponse(responseCode = "401", description = "Token ausente ou invalido")
  @ApiResponse(responseCode = "403", description = "X-Tenant-ID diferente do tenant do token")
  @ApiResponse(
      responseCode = "422",
      description = "Idempotency-Key reutilizada com conteudo diferente")
  @ApiResponse(responseCode = "429", description = "Cota do parceiro excedida")
  @ApiResponse(responseCode = "503", description = "Armazenamento indisponivel; repita a chamada")
  ResponseEntity<ProposalAcceptedResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "UUID gerado pelo parceiro", required = true)
          @RequestHeader(HeaderNames.IDEMPOTENCY_KEY)
          String rawIdempotencyKey,
      @Parameter(description = "Identificador do parceiro", required = true)
          @RequestHeader(HeaderNames.TENANT_ID)
          String rawTenant,
      @Valid @RequestBody ProposalRequest request) {
    IdempotencyKey key = parse("Idempotency-Key", () -> IdempotencyKey.parse(rawIdempotencyKey));
    TenantId tenant = parse("X-Tenant-ID", () -> new TenantId(rawTenant));
    if (!tenant.value().equals(jwt.getClaimAsString(tenantClaim))) {
      throw new TenantForbiddenException();
    }
    AcceptProposalCommand command =
        new AcceptProposalCommand(
            key,
            tenant,
            parse("customerCpf", () -> Cpf.of(request.customerCpf())),
            request.convenioCode(),
            Boolean.TRUE.equals(request.isPortability()),
            parse("amount", () -> Money.of(request.amount().toPlainString())),
            request.installments(),
            request.contractDate());

    AcceptProposalResult result = useCase.accept(command);
    return ResponseEntity.accepted()
        .header("X-Trace-ID", result.tracingId())
        .header(
            "X-Idempotency-Status",
            result.semanticDuplicate()
                ? "DUPLICATE_REJECTED"
                : result.outcome()
                        == br.com.consignado.platform.ingestion.domain.IngestionOutcome.RECEIVED
                    ? "NEW"
                    : "DUPLICATE_ACCEPTED")
        .body(
            new ProposalAcceptedResponse(
                result.outcome().name(),
                result.idempotencyKey().toString(),
                result.tracingId(),
                result.receivedAt().getEpochSecond(),
                result.proposalId()));
  }

  private static <T> T parse(String field, java.util.function.Supplier<T> parser) {
    try {
      return parser.get();
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException(field);
    }
  }
}
