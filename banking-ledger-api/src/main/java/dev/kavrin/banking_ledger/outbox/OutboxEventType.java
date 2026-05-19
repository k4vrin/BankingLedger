package dev.kavrin.banking_ledger.outbox;

public enum OutboxEventType {
    LEDGER_TRANSACTION_POSTED("LedgerTransactionPosted"),
    LEDGER_TRANSACTION_REVERSED("LedgerTransactionReversed"),
    ADJUSTMENT_POSTED("AdjustmentPosted");

    private final String eventName;

    OutboxEventType(String eventName) {
        this.eventName = eventName;
    }

    public String eventName() {
        return eventName;
    }
}
