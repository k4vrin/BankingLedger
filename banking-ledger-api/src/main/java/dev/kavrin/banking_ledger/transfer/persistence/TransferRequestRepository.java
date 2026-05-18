package dev.kavrin.banking_ledger.transfer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransferRequestRepository extends JpaRepository<TransferRequestEntity, UUID> {
}
