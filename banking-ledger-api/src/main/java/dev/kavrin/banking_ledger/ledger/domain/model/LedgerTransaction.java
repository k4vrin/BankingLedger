package dev.kavrin.banking_ledger.ledger.domain.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record LedgerTransaction(
        UUID id,
        String externalReference,
        LedgerTransactionType transactionType,
        long amountMinor,
        String currencyCode,
        String description,
        TransactionStatus status,
        OffsetDateTime createdAt
) {
    public LedgerTransaction {

        Objects.requireNonNull(id);
        Objects.requireNonNull(transactionType);
        Objects.requireNonNull(currencyCode);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);

        if (externalReference != null) {
            externalReference = externalReference.trim();
        }

        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be positive");
        }

        currencyCode = currencyCode.trim();
        if (currencyCode.isBlank()) {
            throw new IllegalArgumentException("currencyCode is required");
        }
    }

    public static LedgerTransaction pending(
            String externalReference,
            LedgerTransactionType transactionType,
            long amountMinor,
            String currencyCode,
            String description
    ) {

        return new LedgerTransaction(
                UUID.randomUUID(),
                externalReference,
                transactionType,
                amountMinor,
                currencyCode,
                description,
                TransactionStatus.PENDING,
                OffsetDateTime.now()
        );
    }

    public LedgerTransaction posted() {
        return new LedgerTransaction(
                id,
                externalReference,
                transactionType,
                amountMinor,
                currencyCode,
                description,
                TransactionStatus.POSTED,
                createdAt
        );
    }
}
