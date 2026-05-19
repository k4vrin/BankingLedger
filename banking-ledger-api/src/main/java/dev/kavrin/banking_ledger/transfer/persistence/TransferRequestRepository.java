package dev.kavrin.banking_ledger.transfer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransferRequestRepository extends JpaRepository<TransferRequestEntity, UUID> {

    Optional<TransferRequestEntity> findByExternalReference(String externalReference);

    boolean existsByExternalReference(String externalReference);

    Optional<TransferRequestEntity> findByLedgerTransaction_Id(UUID ledgerTransactionId);
}
