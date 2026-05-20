package dev.kavrin.banking_ledger.outbox.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountBalanceChangedPayload(
        UUID accountId,
        String currencyCode,
        long ledgerBalanceMinor,
        long availableBalanceMinor,
        OffsetDateTime changedAt
) {
}