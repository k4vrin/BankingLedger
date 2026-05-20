package dev.kavrin.banking_ledger.reconciliation.persistence;

import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationMismatchType;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationResultStatus;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResultEntity, UUID> {

    Page<ReconciliationResultEntity> findByBatch_IdOrderByCreatedAtDescIdDesc(UUID batchId, Pageable pageable);

    Page<ReconciliationResultEntity> findByBatch_IdAndMismatchTypeOrderByCreatedAtDescIdDesc(
            UUID batchId,
            ReconciliationMismatchType mismatchType,
            Pageable pageable
    );

    Page<ReconciliationResultEntity> findByBatch_IdAndSeverityOrderByCreatedAtDescIdDesc(
            UUID batchId,
            ReconciliationSeverity severity,
            Pageable pageable
    );

    Page<ReconciliationResultEntity> findByBatch_IdAndStatusOrderByCreatedAtDescIdDesc(
            UUID batchId,
            ReconciliationResultStatus status,
            Pageable pageable
    );
}
