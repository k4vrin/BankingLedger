package dev.kavrin.banking_ledger.reconciliation.api.controller;

import dev.kavrin.banking_ledger.reconciliation.api.dto.CreateSettlementBatchRequest;
import dev.kavrin.banking_ledger.reconciliation.api.dto.ReconciliationResultSummaryResponse;
import dev.kavrin.banking_ledger.reconciliation.api.dto.SettlementBatchResponse;
import dev.kavrin.banking_ledger.reconciliation.application.command.CreateSettlementBatchCommand;
import dev.kavrin.banking_ledger.reconciliation.application.query.GetSettlementBatchByIdQuery;
import dev.kavrin.banking_ledger.reconciliation.application.query.SearchReconciliationResultsQuery;
import dev.kavrin.banking_ledger.reconciliation.application.query.SearchSettlementBatchesQuery;
import dev.kavrin.banking_ledger.reconciliation.application.service.CreateSettlementBatchUseCase;
import dev.kavrin.banking_ledger.reconciliation.application.service.ReconciliationQueryUseCase;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationMismatchType;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationResultStatus;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationSeverity;
import dev.kavrin.banking_ledger.reconciliation.domain.model.SettlementBatchStatus;
import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ops/reconciliation/batches")
public class ReconciliationController {

    private final CreateSettlementBatchUseCase createSettlementBatchUseCase;
    private final ReconciliationQueryUseCase reconciliationQueryUseCase;

    @PostMapping
    @PreAuthorize("hasAnyRole('OPS_ADMIN', 'SERVICE')")
    public ResponseEntity<SettlementBatchResponse> createBatch(
            @Valid @RequestBody CreateSettlementBatchRequest request,
            @RequestHeader("X-Correlation-Id") String correlationId,
            @AuthenticationPrincipal AuthenticatedPrincipal principal
    ) {
        var response = createSettlementBatchUseCase.handle(new CreateSettlementBatchCommand(
                request,
                principal.actorType(),
                principal.auditActorRole(),
                principal.actorId(),
                correlationId
        ));

        return ResponseEntity
                .created(URI.create("/api/v1/ops/reconciliation/batches/" + response.id()))
                .body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AUDITOR', 'OPS_ADMIN')")
    public Page<SettlementBatchResponse> searchBatches(
            @RequestParam(required = false) SettlementBatchStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return reconciliationQueryUseCase.searchBatches(new SearchSettlementBatchesQuery(status, page, size));
    }

    @GetMapping("/{batchId}")
    @PreAuthorize("hasAnyRole('AUDITOR', 'OPS_ADMIN')")
    public SettlementBatchResponse getBatch(@PathVariable UUID batchId) {
        return reconciliationQueryUseCase.getById(new GetSettlementBatchByIdQuery(batchId));
    }

    @GetMapping("/{batchId}/results")
    @PreAuthorize("hasAnyRole('AUDITOR', 'OPS_ADMIN')")
    public Page<ReconciliationResultSummaryResponse> searchResults(
            @PathVariable UUID batchId,
            @RequestParam(required = false) ReconciliationMismatchType mismatchType,
            @RequestParam(required = false) ReconciliationSeverity severity,
            @RequestParam(required = false) ReconciliationResultStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return reconciliationQueryUseCase.searchResults(new SearchReconciliationResultsQuery(
                batchId,
                mismatchType,
                severity,
                status,
                page,
                size
        ));
    }
}
