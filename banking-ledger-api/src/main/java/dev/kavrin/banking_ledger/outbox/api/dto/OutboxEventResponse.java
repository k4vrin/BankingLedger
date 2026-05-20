package dev.kavrin.banking_ledger.outbox.api.dto;

import dev.kavrin.banking_ledger.outbox.domain.model.OutboxStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OutboxEventResponse(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String destination,
        String correlationId,
        OutboxStatus status,
        int retryCount,
        OffsetDateTime nextRetryAt,
        String lastErrorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime publishedAt
) {
}
