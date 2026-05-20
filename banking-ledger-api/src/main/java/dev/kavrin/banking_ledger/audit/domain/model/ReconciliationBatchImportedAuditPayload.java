package dev.kavrin.banking_ledger.audit.domain.model;

import java.util.UUID;

public record ReconciliationBatchImportedAuditPayload(
        UUID batchId,
        String source,
        int itemCount,
        int matchedCount,
        int mismatchCount,
        String correlationId
) {
}
