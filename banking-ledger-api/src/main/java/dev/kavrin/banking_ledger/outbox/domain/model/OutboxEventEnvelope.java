package dev.kavrin.banking_ledger.outbox.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OutboxEventEnvelope<T>(
        UUID eventId,
        OutboxEventType eventType,
        String aggregateId,
        OffsetDateTime occurredAt,
        int schemaVersion,
        T data
) {
}