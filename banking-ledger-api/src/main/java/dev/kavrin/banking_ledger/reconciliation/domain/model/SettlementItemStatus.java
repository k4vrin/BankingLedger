package dev.kavrin.banking_ledger.reconciliation.domain.model;

public enum SettlementItemStatus {
    SETTLED(true),
    PENDING(false),
    FAILED(false),
    REJECTED(false),
    REVERSED(true);

    private final boolean positiveAmountRequired;

    SettlementItemStatus(boolean positiveAmountRequired) {
        this.positiveAmountRequired = positiveAmountRequired;
    }

    public boolean requiresPositiveAmount() {
        return positiveAmountRequired;
    }
}
