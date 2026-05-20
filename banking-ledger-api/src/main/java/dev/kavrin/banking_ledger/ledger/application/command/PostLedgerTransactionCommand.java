package dev.kavrin.banking_ledger.ledger.application.command;


import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.ledger.domain.model.LedgerTransactionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record PostLedgerTransactionCommand(

        String externalReference,

        LedgerTransactionType transactionType,

        String currencyCode,

        long amountMinor,

        String description,

        AuditActorType actorType,

        AuditActorRole actorRole,

        String actorId,

        String correlationId,

        List<PostingLineCommand> postingLines

) {
    public PostLedgerTransactionCommand(
            String externalReference,
            LedgerTransactionType transactionType,
            String currencyCode,
            long amountMinor,
            String description,
            AuditActorType actorType,
            String correlationId,
            List<PostingLineCommand> postingLines
    ) {
        this(externalReference, transactionType, currencyCode, amountMinor, description, actorType, null, null,
                correlationId, postingLines);
    }

    public PostLedgerTransactionCommand {
        Objects.requireNonNull(transactionType, "transactionType is required");
        currencyCode = requireText(currencyCode, "currencyCode");
        actorType = actorType == null ? AuditActorType.SYSTEM : actorType;
        actorRole = actorRole == null ? AuditActorRole.SYSTEM : actorRole;
        actorId = actorId == null || actorId.isBlank() ? null : actorId.trim();
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
