package dev.kavrin.banking_ledger.audit.application.command;

import dev.kavrin.banking_ledger.audit.domain.model.*;

import java.util.UUID;

public record WriteAuditEventCommand(
        AuditEventType eventType,
        AuditEntityType entityType,
        UUID entityId,
        AuditActorType actorType,
        AuditActorRole actorRole,
        String actorId,
        String correlationId,
        AuditChannel channel,
        Object payload
) {
}
