package dev.kavrin.banking_ledger.adjustment.api.controller;

import dev.kavrin.banking_ledger.adjustment.api.dto.AdjustmentResponse;
import dev.kavrin.banking_ledger.adjustment.api.dto.CreateAdjustmentRequest;
import dev.kavrin.banking_ledger.adjustment.application.command.CreateAdjustmentCommand;
import dev.kavrin.banking_ledger.adjustment.application.service.CreateAdjustmentUseCase;
import dev.kavrin.banking_ledger.ledger.application.command.PostingLineCommand;
import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ops/adjustments")
public class AdjustmentController {

    private final CreateAdjustmentUseCase createAdjustmentUseCase;

    @PostMapping
    @PreAuthorize("hasAnyRole('OPS_ADMIN', 'SERVICE')")
    public ResponseEntity<AdjustmentResponse> createAdjustment(
            @Valid @RequestBody CreateAdjustmentRequest request,
            @RequestHeader("X-Correlation-Id") String correlationId,
            @AuthenticationPrincipal AuthenticatedPrincipal principal
    ) {
        AdjustmentResponse response = createAdjustmentUseCase.handle(
                new CreateAdjustmentCommand(
                        request.currencyCode(),
                        request.amountMinor(),
                        request.reasonCode(),
                        request.reasonDetail(),
                        principal.requestedByActorType(),
                        principal.auditActorRole(),
                        principal.actorId(),
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
