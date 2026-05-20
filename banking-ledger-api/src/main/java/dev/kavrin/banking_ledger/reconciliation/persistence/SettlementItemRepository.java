package dev.kavrin.banking_ledger.reconciliation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementItemRepository extends JpaRepository<SettlementItemEntity, UUID> {

    List<SettlementItemEntity> findByBatch_IdOrderByExternalTransactionReferenceAscIdAsc(UUID batchId);

    Optional<SettlementItemEntity> findBySourceAndExternalTransactionReference(
            String source,
            String externalTransactionReference
    );
}
