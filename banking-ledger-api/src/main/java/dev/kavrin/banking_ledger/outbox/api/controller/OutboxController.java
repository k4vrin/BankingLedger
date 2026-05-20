package dev.kavrin.banking_ledger.outbox.api.controller;

import dev.kavrin.banking_ledger.outbox.api.dto.OutboxEventResponse;
import dev.kavrin.banking_ledger.outbox.application.command.RequeueOutboxEventCommand;
import dev.kavrin.banking_ledger.outbox.application.service.OutboxQueryUseCase;
import dev.kavrin.banking_ledger.outbox.application.service.RequeueOutboxEventUseCase;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxStatus;
import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ops/outbox/events")
@RequiredArgsConstructor
public class OutboxController {

    private final OutboxQueryUseCase outboxQueryUseCase;
    private final RequeueOutboxEventUseCase requeueOutboxEventUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('AUDITOR', 'OPS_ADMIN')")
    public Page<OutboxEventResponse> search(
            @RequestParam OutboxStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return outboxQueryUseCase.searchByStatus(status, page, size);
    }

    @GetMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('AUDITOR', 'OPS_ADMIN')")
    public OutboxEventResponse getById(@PathVariable UUID eventId) {
        return outboxQueryUseCase.getById(eventId);
    }

    @PostMapping("/{eventId}/requeue")
    @PreAuthorize("hasAnyRole('OPS_ADMIN', 'SERVICE')")
    public OutboxEventResponse requeue(
            @PathVariable UUID eventId,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestParam(defaultValue = "false") boolean resetRetryCount,
            @RequestHeader("X-Correlation-Id") String correlationId,
            @AuthenticationPrincipal AuthenticatedPrincipal principal
    ) {
        return requeueOutboxEventUseCase.handle(new RequeueOutboxEventCommand(
                eventId,
                force,
                resetRetryCount,
                principal.actorType(),
                principal.auditActorRole(),
                principal.actorId(),
                correlationId
        ));
    }
}
