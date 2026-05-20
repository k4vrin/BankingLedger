package dev.kavrin.banking_ledger.audit.domain.model;

import java.util.UUID;

public record AccountCreatedAuditPayload(
        UUID accountId
) {
}
