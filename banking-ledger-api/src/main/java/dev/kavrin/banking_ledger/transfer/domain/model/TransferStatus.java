package dev.kavrin.banking_ledger.transfer.domain.model;

public enum TransferStatus {
    PENDING,
    COMPLETED,
    REJECTED,
    REVERSED,
    FAILED
}
