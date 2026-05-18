package dev.kavrin.banking_ledger.ledger.application.command;


import dev.kavrin.banking_ledger.ledger.domain.model.PostingDirection;

import java.util.Objects;
import java.util.UUID;

public record PostingLineCommand(

        UUID accountId,

        PostingDirection direction,

        long amountMinor,

        String currencyCode

) {
    public PostingLineCommand {
        Objects.requireNonNull(accountId, "accountId is required");
        Objects.requireNonNull(direction, "direction is required");
        Objects.requireNonNull(currencyCode, "currencyCode is required");

        currencyCode = currencyCode.trim();
        if (currencyCode.isBlank()) {
            throw new IllegalArgumentException("currencyCode is required");
        }

        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be positive");
        }
    }

}
