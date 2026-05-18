package dev.kavrin.banking_ledger.outbox.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
}
