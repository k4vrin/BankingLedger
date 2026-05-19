package dev.kavrin.banking_ledger.audit.domain.model;

public enum AuditEventType {
    ACCOUNT_CREATED,
    LEDGER_TRANSACTION_POSTED,
    TRANSFER_REVERSED,
    ADJUSTMENT_POSTED
}
