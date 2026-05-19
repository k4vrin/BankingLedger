package dev.kavrin.banking_ledger.outbox;

public enum OutboxAggregateType {
    LEDGER_TRANSACTION,
    TRANSFER,
    ADJUSTMENT
}
