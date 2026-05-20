package dev.kavrin.banking_ledger.reconciliation.application.command;

import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.reconciliation.api.dto.CreateSettlementBatchRequest;

public record CreateSettlementBatchCommand(
        CreateSettlementBatchRequest request,
        AuditActorType actorType,
        AuditActorRole actorRole,
        String actorId,
        String correlationId
) {
}
