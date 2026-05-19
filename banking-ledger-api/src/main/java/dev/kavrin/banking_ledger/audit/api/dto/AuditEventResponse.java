package dev.kavrin.banking_ledger.audit.api.dto;

import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEntityType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEventType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        AuditEventType eventType,
        AuditEntityType entityType,
        UUID entityId,
        AuditActorType actorType,
        AuditActorRole actorRole,
        String actorId,
        String channel,
        String correlationId,
        OffsetDateTime createdAt,
        String payload
) {
}
