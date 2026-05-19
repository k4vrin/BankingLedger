package dev.kavrin.banking_ledger.adjustment.api.controller;

import dev.kavrin.banking_ledger.adjustment.api.dto.AdjustmentResponse;
import dev.kavrin.banking_ledger.adjustment.api.dto.CreateAdjustmentRequest;
import dev.kavrin.banking_ledger.adjustment.application.command.CreateAdjustmentCommand;
import dev.kavrin.banking_ledger.adjustment.application.service.CreateAdjustmentUseCase;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.ledger.application.command.PostingLineCommand;
import dev.kavrin.banking_ledger.transfer.domain.model.RequestedByActorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ops/adjustments")
public class AdjustmentController {

    private final CreateAdjustmentUseCase createAdjustmentUseCase;

    @PostMapping
    public ResponseEntity<AdjustmentResponse> createAdjustment(
            @Valid @RequestBody CreateAdjustmentRequest request,
            @RequestHeader("X-Correlation-Id") String correlationId,
            @RequestHeader("X-Actor-Type") RequestedByActorType actorType,
            @RequestHeader("X-Actor-Role") AuditActorRole actorRole,
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId
    ) {
        AdjustmentResponse response = createAdjustmentUseCase.handle(
                new CreateAdjustmentCommand(
                        request.currencyCode(),
                        request.amountMinor(),
                        request.reasonCode(),
                        request.reasonDetail(),
                        actorType,
                        actorRole,
                        actorId,
                        correlationId,
                        request.postingLines()
                                .stream()
                                .map(line -> new PostingLineCommand(
                                        line.accountId(),
                                        line.direction(),
                                        line.amountMinor(),
                                        line.currencyCode()
                                ))
                                .toList()
                )
        );

        return ResponseEntity
                .created(URI.create("/api/v1/ops/adjustments/" + response.id()))
                .body(response);
    }
}