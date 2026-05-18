package dev.kavrin.banking_ledger.ledger.persistence.repository;

import dev.kavrin.banking_ledger.ledger.persistence.entity.LedgerTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransactionEntity, UUID> {
    boolean existsByExternalReference(String externalReference);
}
