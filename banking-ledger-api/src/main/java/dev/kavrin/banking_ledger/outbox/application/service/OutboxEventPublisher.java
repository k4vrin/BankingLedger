package dev.kavrin.banking_ledger.outbox.application.service;

import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventEntity;

public interface OutboxEventPublisher {
    void publish(OutboxEventEntity event);
}
