package dev.kavrin.banking_ledger.outbox;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED,
    DEAD_LETTER
}
