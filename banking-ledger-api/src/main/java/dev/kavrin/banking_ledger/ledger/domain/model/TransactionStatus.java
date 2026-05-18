package dev.kavrin.banking_ledger.ledger.domain.model;

public enum TransactionStatus {
    PENDING,
    POSTED,
    REJECTED,
    REVERSED,
    FAILED
}
