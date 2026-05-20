package dev.kavrin.banking_ledger.outbox.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LedgerTransactionPostedPayload(
        UUID ledgerTransactionId,
        String transactionReference,
        String currencyCode,
        long amountMinor,
        OffsetDateTime postedAt
) {
}