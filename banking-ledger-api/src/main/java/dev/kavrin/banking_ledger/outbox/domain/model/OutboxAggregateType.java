package dev.kavrin.banking_ledger.outbox.domain.model;

public enum OutboxAggregateType {
    LEDGER_TRANSACTION,
    TRANSFER,
    ADJUSTMENT,
    RECONCILIATION_BATCH
}
