package dev.kavrin.banking_ledger.ledger.application.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PostedLedgerTransactionResult(
        UUID ledgerTransactionId,
        UUID journalEntryId,
        List<UUID> postingIds,
        OffsetDateTime postedAt
) {
    public PostedLedgerTransactionResult {
        postingIds = List.copyOf(postingIds);
    }
}
