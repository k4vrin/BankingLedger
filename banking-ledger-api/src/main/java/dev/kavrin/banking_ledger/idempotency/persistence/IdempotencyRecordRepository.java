package dev.kavrin.banking_ledger.idempotency.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, UUID> {
}
