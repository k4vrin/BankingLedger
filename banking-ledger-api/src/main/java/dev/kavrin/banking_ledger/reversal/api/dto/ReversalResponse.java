package dev.kavrin.banking_ledger.reversal.api.dto;

import dev.kavrin.banking_ledger.reversal.domain.model.ReversalReasonCode;
import dev.kavrin.banking_ledger.reversal.domain.model.ReversalStatus;
import dev.kavrin.banking_ledger.transfer.domain.model.RequestedByActorType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReversalResponse(
        UUID id,

        UUID originalTransferId,
        UUID originalLedgerTransactionId,
        UUID reversalLedgerTransactionId,

        ReversalStatus status,

        ReversalReasonCode reasonCode,
        String reasonDetail,

        RequestedByActorType requestedByActorType,
        String requestedByActorRole,
        String requestedByActorId,

        String correlationId,

        OffsetDateTime requestedAt,
        OffsetDateTime completedAt,

        String failureReasonCode,
        String failureReasonDetail,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}