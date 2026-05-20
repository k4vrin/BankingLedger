package dev.kavrin.banking_ledger.outbox.application.service;

import dev.kavrin.banking_ledger.outbox.api.dto.OutboxEventResponse;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventEntity;
import org.springframework.stereotype.Component;

@Component
public class OutboxResponseMapper {

    public OutboxEventResponse toResponse(OutboxEventEntity event) {
        return new OutboxEventResponse(
                event.getId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getDestination(),
                event.getCorrelationId(),
                event.getStatus(),
                event.getRetryCount(),
                event.getNextRetryAt(),
                event.getLastErrorMessage(),
                event.getCreatedAt(),
                event.getPublishedAt()
        );
    }
}
