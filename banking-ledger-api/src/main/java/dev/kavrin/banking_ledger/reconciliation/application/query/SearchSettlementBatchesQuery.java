package dev.kavrin.banking_ledger.reconciliation.application.query;

import dev.kavrin.banking_ledger.reconciliation.domain.model.SettlementBatchStatus;

public record SearchSettlementBatchesQuery(
        SettlementBatchStatus status,
        int page,
        int size
) {
}
