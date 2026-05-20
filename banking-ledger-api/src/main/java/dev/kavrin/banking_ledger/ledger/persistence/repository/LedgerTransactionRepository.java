package dev.kavrin.banking_ledger.ledger.persistence.repository;

import dev.kavrin.banking_ledger.ledger.persistence.entity.LedgerTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransactionEntity, UUID> {
    boolean existsByExternalReference(String externalReference);

    List<LedgerTransactionEntity> findByExternalReferenceIn(Collection<String> externalReferences);

    List<LedgerTransactionEntity> findByStatusInAndPostedAtGreaterThanEqualAndPostedAtLessThanAndExternalReferenceNotInOrderByExternalReferenceAscIdAsc(
            Collection<dev.kavrin.banking_ledger.ledger.domain.model.TransactionStatus> statuses,
            OffsetDateTime from,
            OffsetDateTime to,
            Collection<String> externalReferences
    );
}
