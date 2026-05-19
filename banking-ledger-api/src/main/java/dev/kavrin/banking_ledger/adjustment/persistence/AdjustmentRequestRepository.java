package dev.kavrin.banking_ledger.adjustment.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdjustmentRequestRepository extends JpaRepository<AdjustmentRequestEntity, UUID> {

    Optional<AdjustmentRequestEntity> findByLedgerTransaction_Id(UUID ledgerTransactionId);
}
