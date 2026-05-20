package dev.kavrin.banking_ledger.reconciliation.persistence;

import dev.kavrin.banking_ledger.reconciliation.domain.model.SettlementBatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SettlementBatchRepository extends JpaRepository<SettlementBatchEntity, UUID> {

    Page<SettlementBatchEntity> findByStatusOrderByImportedAtDescIdDesc(
            SettlementBatchStatus status,
            Pageable pageable
    );
}
