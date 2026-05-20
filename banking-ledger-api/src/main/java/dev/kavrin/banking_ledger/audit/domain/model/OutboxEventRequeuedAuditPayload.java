package dev.kavrin.banking_ledger.audit.domain.model;

import java.util.UUID;

public record OutboxEventRequeuedAuditPayload(
        UUID outboxEventId,
        String previousStatus,
        String newStatus,
        boolean forced,
        boolean retryCountReset,
        int retryCount
) {
}
