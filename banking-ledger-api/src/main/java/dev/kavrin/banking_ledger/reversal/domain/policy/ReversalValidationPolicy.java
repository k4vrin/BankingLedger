package dev.kavrin.banking_ledger.reversal.domain.policy;

import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.reversal.application.command.ReverseTransferCommand;
import dev.kavrin.banking_ledger.shared.error.*;
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
                    AuditActorRole.SERVICE
            );

    public static void validateRequest(ReverseTransferCommand command) {
        if (command == null) {
            throw invalidRequest("command is required");
        }
        if (command.transferId() == null) {
            throw invalidRequest("transferId is required");
        }
        if (command.reasonCode() == null) {
            throw invalidRequest("reasonCode is required");
        }
        if (command.actorType() == null) {
            throw invalidRequest("actorType is required");
        }
        if (command.actorRole() == null) {
            throw invalidRequest("actorRole is required");
        }

        if (command.correlationId() == null || command.correlationId().isBlank()) {
            throw invalidRequest("correlationId is required");
        }

        if (!ALLOWED_ROLES_BEFORE_PHASE_7.contains(command.actorRole())) {
            throw SecurityDomainException.forbidden(
                    ApiErrorCode.Security.FORBIDDEN_RESOURCE,
                    "Actor role is not allowed to reverse transfers: " + command.actorRole(),
                    "Actor role is not allowed to reverse transfers."
            );
        }
    }

    public static void validateTransferCanBeReversed(
            TransferRequestEntity transfer,
            boolean duplicateReversalExists
    ) {
        Objects.requireNonNull(transfer, "transfer is required");

        if (duplicateReversalExists) {
            throw new ConflictException(
                    ApiErrorCode.Business.REVERSAL_ALREADY_EXISTS,
                    "Transfer has already been reversed: " + transfer.getId(),
                    "Transfer has already been reversed."
            );
        }

        if (transfer.getStatus() != TransferStatus.COMPLETED) {
            throw new BusinessRuleViolationException(
                    ApiErrorCode.Business.TRANSFER_NOT_REVERSIBLE,
                    "Only completed transfers can be reversed: " + transfer.getId(),
                    "Only completed transfers can be reversed."
            );
        }

        if (transfer.getLedgerTransaction() == null) {
            throw new BusinessRuleViolationException(
                    ApiErrorCode.Business.TRANSFER_NOT_REVERSIBLE,
                    "Transfer has no ledger transaction: " + transfer.getId(),
                    "Transfer cannot be reversed because it has no ledger transaction."
            );
        }

    }

    private static BadRequestException invalidRequest(String message) {
        return new BadRequestException(
                ApiErrorCode.Validation.INVALID_REQUEST,
                message,
                "Reversal request is invalid."
        );
    }
}
