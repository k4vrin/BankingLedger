package dev.kavrin.banking_ledger.ledger.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransactionEntity, UUID> {
}
