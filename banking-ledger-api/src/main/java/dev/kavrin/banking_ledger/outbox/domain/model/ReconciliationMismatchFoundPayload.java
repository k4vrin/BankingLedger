package dev.kavrin.banking_ledger.outbox.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReconciliationMismatchFoundPayload(
        UUID reconciliationResultId,
        UUID settlementBatchId,
        String mismatchType,
        String settlementReference,
        OffsetDateTime detectedAt
) {
}