package dev.kavrin.banking_ledger.audit.application.query;

import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEntityType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEventType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SearchAuditEventsQuery(
        AuditEventType eventType,
        AuditEntityType entityType,
        UUID entityId,
        AuditActorType actorType,
        AuditActorRole actorRole,
        String actorId,
        String correlationId,
        OffsetDateTime createdFrom,
        OffsetDateTime createdTo,
        int page,
        int size
) {
}
