package dev.kavrin.banking_ledger.ledger.domain.factory;

import dev.kavrin.banking_ledger.ledger.domain.model.JournalEntry;
import dev.kavrin.banking_ledger.ledger.domain.model.LedgerTransaction;

public record PostedLedgerGraph(
        LedgerTransaction ledgerTransaction,
        JournalEntry journalEntry
) {
}
