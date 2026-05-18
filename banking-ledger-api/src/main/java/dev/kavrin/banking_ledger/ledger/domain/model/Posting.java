package dev.kavrin.banking_ledger.ledger.domain.model;

import java.util.Objects;
import java.util.UUID;

public record Posting(
        UUID id,
        UUID accountId,
        PostingDirection direction,
        long amountMinor,
        String currencyCode
) {
    public Posting {
        Objects.requireNonNull(id);
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(direction);
        Objects.requireNonNull(currencyCode);

        currencyCode = currencyCode.trim();
        if (currencyCode.isBlank()) {
            throw new IllegalArgumentException("currencyCode is required");
        }

        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be positive");
        }
    }

    public static Posting debit(UUID accountId, long amountMinor, String currencyCode) {
        return new Posting(
                UUID.randomUUID(),
                accountId,
                PostingDirection.DEBIT,
                amountMinor,
                currencyCode
        );
    }

    public static Posting credit(UUID accountId, long amountMinor, String currencyCode) {
        return new Posting(
                UUID.randomUUID(),
                accountId,
                PostingDirection.CREDIT,
                amountMinor,
                currencyCode
        );
    }
}
