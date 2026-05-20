package dev.kavrin.banking_ledger.reconciliation.domain.policy;

public record SettlementBatchValidationError(
        String field,
        String message
) {
}
