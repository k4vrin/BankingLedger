package dev.kavrin.banking_ledger.audit.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
}
