package dev.kavrin.banking_ledger.outbox.domain.model;

public final class OutboxEventSchemaVersions {

    public static final int LEDGER_TRANSACTION_POSTED_V1 = 1;
    public static final int LEDGER_TRANSACTION_REVERSED_V1 = 1;
    public static final int ADJUSTMENT_POSTED_V1 = 1;
    public static final int ACCOUNT_BALANCE_CHANGED_V1 = 1;
    public static final int RECONCILIATION_MISMATCH_FOUND_V1 = 1;
    public static final int RECONCILIATION_COMPLETED_V1 = 1;
    private OutboxEventSchemaVersions() {
    }
}
