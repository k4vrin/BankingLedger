package dev.kavrin.banking_ledger.ledger.domain.model;

public enum LedgerTransactionType {
    TRANSFER,
    REVERSAL,
    FEE,
    ADJUSTMENT;

    public JournalEntryType toJournalEntryType() {
        return switch (this) {
            case TRANSFER -> JournalEntryType.TRANSFER;
            case REVERSAL -> JournalEntryType.REVERSAL;
            case FEE -> JournalEntryType.FEE;
            case ADJUSTMENT -> JournalEntryType.ADJUSTMENT;
        };
    }
}
