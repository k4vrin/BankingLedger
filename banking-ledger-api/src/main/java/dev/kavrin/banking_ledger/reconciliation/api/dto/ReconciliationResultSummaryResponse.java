package dev.kavrin.banking_ledger.reconciliation.api.dto;

import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationMismatchType;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationResultStatus;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationSeverity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReconciliationResultSummaryResponse(
        UUID id,
        UUID batchId,
        UUID itemId,
        UUID ledgerTransactionId,
        ReconciliationMismatchType mismatchType,
        ReconciliationSeverity severity,
        ReconciliationResultStatus status,
        String detail,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt
) {
}
