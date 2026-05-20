package dev.kavrin.banking_ledger.reconciliation.application.query;

import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationMismatchType;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationResultStatus;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationSeverity;

import java.util.UUID;

public record SearchReconciliationResultsQuery(
        UUID batchId,
        ReconciliationMismatchType mismatchType,
        ReconciliationSeverity severity,
        ReconciliationResultStatus status,
        int page,
        int size
) {
}
