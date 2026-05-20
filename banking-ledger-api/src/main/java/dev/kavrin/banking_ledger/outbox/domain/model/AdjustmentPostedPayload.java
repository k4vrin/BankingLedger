package dev.kavrin.banking_ledger.outbox.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdjustmentPostedPayload(
        UUID adjustmentId,
        UUID ledgerTransactionId,
        String reasonCode,
        OffsetDateTime postedAt
) {
}