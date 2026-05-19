package dev.kavrin.banking_ledger.idempotency.persistence.repository;

import dev.kavrin.banking_ledger.idempotency.persistence.entity.IdempotencyRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, UUID> {

    Optional<IdempotencyRecordEntity> findByOperationScopeAndIdempotencyKey(
            String operationScope,
            String idempotencyKey
    );

}
