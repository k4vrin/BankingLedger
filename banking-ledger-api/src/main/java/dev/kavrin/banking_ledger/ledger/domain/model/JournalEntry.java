package dev.kavrin.banking_ledger.ledger.domain.model;

import dev.kavrin.banking_ledger.ledger.domain.policy.DoubleEntryPostingPolicy;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
public final class JournalEntry {

    private final UUID id;
    private final UUID ledgerTransactionId;
    private final String currencyCode;
    private final List<Posting> postings;
    private final OffsetDateTime createdAt;

    public JournalEntry(
            UUID id,
            UUID ledgerTransactionId,
            String currencyCode,
            List<Posting> postings,
            OffsetDateTime createdAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.ledgerTransactionId = Objects.requireNonNull(ledgerTransactionId);
        this.currencyCode = Objects.requireNonNull(currencyCode).trim();
        this.createdAt = Objects.requireNonNull(createdAt);

        if (this.currencyCode.isBlank()) {
            throw new IllegalArgumentException("currencyCode is required");
        }

        DoubleEntryPostingPolicy.validate(this.currencyCode, postings);

        this.postings = List.copyOf(postings);
    }

    public static JournalEntry create(
            UUID ledgerTransactionId,
            String currencyCode,
            List<Posting> postings
    ) {
        return new JournalEntry(
                UUID.randomUUID(),
                ledgerTransactionId,
                currencyCode,
                postings,
                OffsetDateTime.now()
        );
    }
}
