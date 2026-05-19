package dev.kavrin.banking_ledger.security.domain;

import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.transfer.domain.model.RequestedByActorType;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedPrincipal(
        String subject,
        String actorId,
        AuditActorType actorType,
        Set<SecurityRole> roles,
        UUID customerId,
        String tokenId
) {
    public boolean hasRole(SecurityRole role) {
        return roles.contains(role);
    }

    public RequestedByActorType requestedByActorType() {
        if (roles.contains(SecurityRole.CUSTOMER)) {
            return RequestedByActorType.CUSTOMER;
        }
        if (roles.contains(SecurityRole.TELLER)) {
            return RequestedByActorType.TELLER;
        }
        if (roles.contains(SecurityRole.OPS_ADMIN)) {
            return RequestedByActorType.OPS_ADMIN;
        }
        if (roles.contains(SecurityRole.SERVICE)) {
            return RequestedByActorType.SERVICE;
        }
        return RequestedByActorType.SYSTEM;
    }

    public AuditActorRole auditActorRole() {
        if (roles.contains(SecurityRole.OPS_ADMIN)) {
            return AuditActorRole.OPS_ADMIN;
        }
        if (roles.contains(SecurityRole.SERVICE)) {
            return AuditActorRole.SERVICE;
        }
        if (roles.contains(SecurityRole.AUDITOR)) {
            return AuditActorRole.AUDITOR;
        }
        if (roles.contains(SecurityRole.TELLER)) {
            return AuditActorRole.TELLER;
        }
        if (roles.contains(SecurityRole.CUSTOMER)) {
            return AuditActorRole.CUSTOMER;
        }
        return AuditActorRole.SYSTEM;
    }
}
