package dev.kavrin.banking_ledger.adjustment.api.dto;

import dev.kavrin.banking_ledger.adjustment.domain.model.AdjustmentReasonCode;
import dev.kavrin.banking_ledger.adjustment.domain.model.AdjustmentStatus;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.transfer.domain.model.RequestedByActorType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdjustmentResponse(
        UUID id,
        UUID ledgerTransactionId,

        AdjustmentReasonCode reasonCode,
        String reasonDetail,

        RequestedByActorType requestedByActorType,
        AuditActorRole requestedByActorRole,
        String requestedByActorId,
        String correlationId,

        AdjustmentStatus status,

        OffsetDateTime requestedAt,
        OffsetDateTime completedAt,

        String failureReasonCode,
        String failureReasonDetail,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}