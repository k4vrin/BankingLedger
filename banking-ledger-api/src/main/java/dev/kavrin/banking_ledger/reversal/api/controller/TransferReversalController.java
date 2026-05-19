package dev.kavrin.banking_ledger.reversal.api.controller;

import dev.kavrin.banking_ledger.reversal.api.dto.ReversalResponse;
import dev.kavrin.banking_ledger.reversal.api.dto.ReverseTransferRequest;
import dev.kavrin.banking_ledger.reversal.application.command.ReverseTransferCommand;
import dev.kavrin.banking_ledger.reversal.application.service.ReverseTransferUseCase;
import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/transfers")
public class TransferReversalController {

    private final ReverseTransferUseCase reverseTransferUseCase;

    @PostMapping("/{transferId}/reverse")
    @PreAuthorize("hasAnyRole('OPS_ADMIN', 'SERVICE')")
    public ResponseEntity<ReversalResponse> reverseTransfer(
            @PathVariable UUID transferId,
            @Valid @RequestBody ReverseTransferRequest request,
            @RequestHeader("X-Correlation-Id") String correlationId,
            @AuthenticationPrincipal AuthenticatedPrincipal principal
    ) {
        ReversalResponse response = reverseTransferUseCase.handle(
                new ReverseTransferCommand(
                        transferId,
                        request.reasonCode(),
                        request.reasonDetail(),
                        principal.requestedByActorType(),
                        principal.auditActorRole(),
                        principal.actorId(),
                        correlationId
                )
        );

        return ResponseEntity
                .created(URI.create("/api/v1/transfers/" + transferId + "/reversal"))
                .body(response);
    }
}
