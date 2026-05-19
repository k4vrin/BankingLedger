package dev.kavrin.banking_ledger.reversal.domain.policy;

import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.reversal.application.command.ReverseTransferCommand;
import dev.kavrin.banking_ledger.transfer.domain.model.TransferStatus;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestEntity;
import lombok.NoArgsConstructor;

import java.util.EnumSet;
import java.util.Objects;

@NoArgsConstructor
public final class ReversalValidationPolicy {

    private static final EnumSet<AuditActorRole> ALLOWED_ROLES_BEFORE_PHASE_7 =
            EnumSet.of(
                    AuditActorRole.OPS_ADMIN,
                    AuditActorRole.TELLER
            );

    public static void validateRequest(ReverseTransferCommand command) {
        Objects.requireNonNull(command, "command is required");
        Objects.requireNonNull(command.transferId(), "transferId is required");
        Objects.requireNonNull(command.reasonCode(), "reasonCode is required");
        Objects.requireNonNull(command.actorType(), "actorType is required");
        Objects.requireNonNull(command.actorRole(), "actorRole is required");

        if (command.correlationId() == null || command.correlationId().isBlank()) {
            throw new IllegalArgumentException("correlationId is required");
        }

        if (!ALLOWED_ROLES_BEFORE_PHASE_7.contains(command.actorRole())) {
            throw new IllegalArgumentException("actor role is not allowed to reverse transfers");
        }
    }

    public static void validateTransferCanBeReversed(
            TransferRequestEntity transfer,
            boolean duplicateReversalExists
    ) {
        Objects.requireNonNull(transfer, "transfer is required");

        if (transfer.getStatus() != TransferStatus.COMPLETED) {
            throw new IllegalArgumentException("only completed transfers can be reversed");
        }

        if (transfer.getLedgerTransaction() == null) {
            throw new IllegalArgumentException("transfer has no ledger transaction");
        }

        if (duplicateReversalExists) {
            throw new IllegalArgumentException("transfer has already been reversed");
        }
    }
}