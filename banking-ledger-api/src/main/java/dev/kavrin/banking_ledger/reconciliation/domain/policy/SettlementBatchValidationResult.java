package dev.kavrin.banking_ledger.reconciliation.domain.policy;

import java.util.List;

public record SettlementBatchValidationResult(
        List<SettlementBatchValidationError> errors
) {

    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean hasErrors() {
        return !isValid();
    }
}
