package dev.kavrin.banking_ledger.audit.domain.model;

public enum AuditEntityType {
    ACCOUNT,
    LEDGER_TRANSACTION,
    TRANSFER,
    ADJUSTMENT,
    RECONCILIATION_BATCH
}
