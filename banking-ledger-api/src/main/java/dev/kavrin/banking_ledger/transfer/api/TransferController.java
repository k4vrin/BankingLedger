package dev.kavrin.banking_ledger.transfer.api;

import dev.kavrin.banking_ledger.idempotency.application.service.IdempotencyKeyValidator;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;
import dev.kavrin.banking_ledger.transfer.api.dto.CreateTransferRequest;
import dev.kavrin.banking_ledger.transfer.application.command.CreateTransferCommand;
import dev.kavrin.banking_ledger.transfer.application.service.CreateTransferUseCase;
import dev.kavrin.banking_ledger.transfer.domain.model.RequestedByActorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final CreateTransferUseCase createTransferUseCase;
    private final IdempotencyKeyValidator idempotencyKeyValidator;

    @PostMapping
    public ResponseEntity<String> createTransfer(
            @Valid @RequestBody CreateTransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
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
                parseActorType(actorType),
                correlationId
        ));

        var builder = ResponseEntity.status(result.statusCode())
                .contentType(MediaType.APPLICATION_JSON);

        if (!result.replayed()) {
            builder.location(URI.create("/api/v1/transfers/" + result.transferId()));
        }

        return builder.body(result.responseBody());
    }

    private RequestedByActorType parseActorType(String actorType) {
        if (actorType == null || actorType.isBlank()) {
            return RequestedByActorType.SYSTEM;
        }

        try {
            return RequestedByActorType.valueOf(actorType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    ApiErrorCode.Validation.INVALID_REQUEST,
                    "Invalid actor type: " + actorType,
                    "Invalid actor type."
            );
        }
    }
}
