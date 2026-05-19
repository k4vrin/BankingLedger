package dev.kavrin.banking_ledger.reversal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReversalRepository extends JpaRepository<ReversalEntity, UUID> {

    Optional<ReversalEntity> findByOriginalTransfer_Id(UUID originalTransferId);
    Optional<ReversalEntity> findByOriginalLedgerTransaction_Id(UUID originalLedgerTransactionId);
    boolean existsByOriginalTransfer_Id(UUID originalTransferId);
}
