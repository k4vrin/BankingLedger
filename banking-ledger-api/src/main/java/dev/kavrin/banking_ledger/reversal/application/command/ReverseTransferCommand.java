package dev.kavrin.banking_ledger.reversal.application.command;

import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.reversal.domain.model.ReversalReasonCode;
import dev.kavrin.banking_ledger.transfer.domain.model.RequestedByActorType;

import java.util.Objects;
import java.util.UUID;

public record ReverseTransferCommand(
        UUID transferId,
        ReversalReasonCode reasonCode,
        String reasonDetail,
        RequestedByActorType actorType,
        AuditActorRole actorRole,
        String actorId,
        String correlationId
) {
    public ReverseTransferCommand {
        Objects.requireNonNull(transferId, "transferId is required");
        Objects.requireNonNull(reasonCode, "reasonCode is required");
        Objects.requireNonNull(actorType, "actorType is required");

        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId is required");
        }

        reasonDetail = normalizeNullable(reasonDetail);
        actorId = normalizeNullable(actorId);
        correlationId = correlationId.trim();
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}