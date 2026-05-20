package dev.kavrin.banking_ledger.audit.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/**
 * Infrastructure repository for audit persistence. Application code should route audit writes
 * through AuditEventWriter and expose audit data through read-only query/investigation use cases.
 */
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID>, JpaSpecificationExecutor<AuditEventEntity> {
}
