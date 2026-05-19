package dev.kavrin.banking_ledger.transfer.domain.model;

import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;

public enum RequestedByActorType {
    CUSTOMER,
    TELLER,
    OPS_ADMIN,
    SERVICE,
    SYSTEM;

    public AuditActorType toAuditActorType() {
        return switch (this) {
            case CUSTOMER -> AuditActorType.CUSTOMER;
            case TELLER, OPS_ADMIN -> AuditActorType.EMPLOYEE;
            case SERVICE -> AuditActorType.SERVICE;
            case SYSTEM -> AuditActorType.SYSTEM;
        };
    }
}
