package dev.kavrin.banking_ledger.outbox.domain.model;

public enum OutboxAggregateType {
    ACCOUNT,
    LEDGER_TRANSACTION,
    TRANSFER,
    ADJUSTMENT,
    RECONCILIATION_BATCH
}
