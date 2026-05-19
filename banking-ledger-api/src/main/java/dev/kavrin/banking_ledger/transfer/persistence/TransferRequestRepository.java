package dev.kavrin.banking_ledger.transfer.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface TransferRequestRepository extends JpaRepository<TransferRequestEntity, UUID> {

    Optional<TransferRequestEntity> findByExternalReference(String externalReference);

    boolean existsByExternalReference(String externalReference);

    Optional<TransferRequestEntity> findByLedgerTransaction_Id(UUID ledgerTransactionId);

    @Query("""
            select count(t) > 0
            from TransferRequestEntity t
            where t.id = :id
              and (
                  t.sourceAccount.customer.id = :customerId
                  or t.destinationAccount.customer.id = :customerId
              )
            """)
    boolean existsByIdAndCustomerId(UUID id, UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select t
        from TransferRequestEntity t
        where t.id = :id
        """)
    Optional<TransferRequestEntity> findByIdForUpdate(UUID id);
}
