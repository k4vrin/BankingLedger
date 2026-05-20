package dev.kavrin.banking_ledger.outbox.application.command;

import dev.kavrin.banking_ledger.outbox.domain.model.OutboxDestination;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxEventType;

import java.util.UUID;

public record WriteOutboxEventCommand(
        String aggregateType,
        UUID aggregateId,
        OutboxEventType eventType,
        OutboxDestination destination,
        String correlationId,
        Object payload
) {
}