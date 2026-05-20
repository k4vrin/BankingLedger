package dev.kavrin.banking_ledger.outbox.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LedgerTransactionReversedPayload(
        UUID originalLedgerTransactionId,
        UUID reversalLedgerTransactionId,
        String reasonCode,
        OffsetDateTime reversedAt
) {
}