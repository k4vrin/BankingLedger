package dev.kavrin.banking_ledger.reconciliation.api.dto;

import dev.kavrin.banking_ledger.reconciliation.domain.model.SettlementBatchStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SettlementBatchResponse(
        UUID id,
        String source,
        String referenceName,
        String importedByActor,
        String correlationId,
        SettlementBatchStatus status,
        OffsetDateTime importedAt,
        OffsetDateTime completedAt,
        int itemCount,
        int matchedCount,
        int mismatchCount,
        List<SettlementItemResponse> items,
        List<ReconciliationResultSummaryResponse> results
) {
}
