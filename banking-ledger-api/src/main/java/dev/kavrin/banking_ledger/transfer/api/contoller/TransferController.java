package dev.kavrin.banking_ledger.transfer.api.contoller;

import dev.kavrin.banking_ledger.idempotency.application.service.IdempotencyKeyValidator;
import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;
import dev.kavrin.banking_ledger.transfer.api.dto.CreateTransferRequest;
import dev.kavrin.banking_ledger.transfer.api.dto.TransferResponse;
import dev.kavrin.banking_ledger.transfer.application.command.CreateTransferCommand;
import dev.kavrin.banking_ledger.transfer.application.query.GetTransferByIdQuery;
import dev.kavrin.banking_ledger.transfer.application.service.CreateTransferUseCase;
import dev.kavrin.banking_ledger.transfer.application.service.TransferQueryUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final CreateTransferUseCase createTransferUseCase;
    private final TransferQueryUseCase transferQueryUseCase;
    private final IdempotencyKeyValidator idempotencyKeyValidator;

    @PostMapping
    @PreAuthorize("@accountOwnership.canCreateTransfer(authentication.principal, #request.sourceAccountId())")
    public ResponseEntity<String> createTransfer(
            @Valid @RequestBody CreateTransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        var normalizedIdempotencyKey = idempotencyKeyValidator.validateAndNormalize(idempotencyKey);
        var result = createTransferUseCase.handle(new CreateTransferCommand(
                request.sourceAccountId(),
                request.destinationAccountId(),
                CurrencyCode.of(request.currencyCode()),
                request.amountMinor(),
                request.externalReference(),
                request.description(),
                normalizedIdempotencyKey,
                principal.requestedByActorType(),
                correlationId
        ));

        var builder = ResponseEntity.status(result.statusCode())
                .contentType(MediaType.APPLICATION_JSON);

        if (!result.replayed()) {
            builder.location(URI.create("/api/v1/transfers/" + result.transferId()));
        }

        return builder.body(result.responseBody());
    }

    @GetMapping("/{transferId}")
    @PreAuthorize("@accountOwnership.canReadTransfer(authentication.principal, #transferId)")
    public TransferResponse getTransfer(@PathVariable UUID transferId) {
        return transferQueryUseCase.getById(new GetTransferByIdQuery(transferId));
    }
}
