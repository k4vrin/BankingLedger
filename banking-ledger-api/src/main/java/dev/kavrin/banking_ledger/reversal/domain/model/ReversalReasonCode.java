package dev.kavrin.banking_ledger.reversal.domain.model;

public enum ReversalReasonCode {
    CUSTOMER_REQUESTED,
    DUPLICATE_TRANSFER,
    FRAUD,
    OPERATIONAL_ERROR,
    COMPLIANCE,
    OTHER
}
