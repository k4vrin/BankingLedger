package dev.kavrin.banking_ledger.ledger.persistence.repository;

import dev.kavrin.banking_ledger.ledger.persistence.entity.JournalEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, UUID> {
    long countByLedgerTransaction_Id(UUID ledgerTransactionId);

    Optional<JournalEntryEntity> findByLedgerTransaction_Id(UUID ledgerTransactionId);
}
