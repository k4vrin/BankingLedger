package dev.kavrin.banking_ledger.ledger.application.command;


import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record PostLedgerTransactionCommand(

        String externalReference,

        String transactionType,

        String currencyCode,

        long amountMinor,

        String description,

        String actorType,

        String correlationId,

        List<PostingLineCommand> postingLines

) {
    public PostLedgerTransactionCommand {
        transactionType = requireText(transactionType, "transactionType");
        currencyCode = requireText(currencyCode, "currencyCode");
        actorType = actorType == null || actorType.isBlank() ? "SYSTEM" : actorType.trim();
        correlationId = correlationId == null ? null : correlationId.trim();

        if (externalReference != null) {
            externalReference = externalReference.trim();
        }

        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be positive");
        }

        if (postingLines == null || postingLines.isEmpty()) {
            throw new IllegalArgumentException("postingLines must not be empty");
        }

        postingLines = List.copyOf(new ArrayList<>(postingLines));
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is required");
        var normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }
}
