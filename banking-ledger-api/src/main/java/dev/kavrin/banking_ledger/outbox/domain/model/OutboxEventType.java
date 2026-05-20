package dev.kavrin.banking_ledger.outbox.domain.model;

public enum OutboxEventType {
    LEDGER_TRANSACTION_POSTED("LedgerTransactionPosted"),
    LEDGER_TRANSACTION_REVERSED("LedgerTransactionReversed"),
    ADJUSTMENT_POSTED("AdjustmentPosted"),
    ACCOUNT_BALANCE_CHANGED("AccountBalanceChanged"),
    RECONCILIATION_MISMATCH_FOUND("ReconciliationMismatchFound"),
    RECONCILIATION_COMPLETED("ReconciliationCompleted");

    private final String eventName;

    OutboxEventType(String eventName) {
        this.eventName = eventName;
    }

    public String eventName() {
        return eventName;
    }

    public OutboxDestination defaultDestination() {
        return switch (this) {
            case LEDGER_TRANSACTION_POSTED,
                 LEDGER_TRANSACTION_REVERSED,
                 ADJUSTMENT_POSTED -> OutboxDestination.LEDGER_EVENTS;
            case ACCOUNT_BALANCE_CHANGED -> OutboxDestination.ACCOUNT_EVENTS;
            case RECONCILIATION_MISMATCH_FOUND, RECONCILIATION_COMPLETED -> OutboxDestination.RECONCILIATION_EVENTS;
        };
    }
}
