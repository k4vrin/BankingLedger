package dev.kavrin.banking_ledger.audit.application.service;

import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditChannel;

public record AuditRequestContext(
        AuditActorType actorType,
        AuditActorRole actorRole,
        String actorId,
        String correlationId,
        AuditChannel channel
) {
    private static final String SYSTEM_ACTOR_ID = "system";

    public static AuditRequestContext system(String correlationId, AuditChannel channel) {
        return new AuditRequestContext(
                AuditActorType.SYSTEM,
                AuditActorRole.SYSTEM,
                SYSTEM_ACTOR_ID,
                correlationId,
                channel == null ? AuditChannel.SYSTEM : channel
        );
    }
}
