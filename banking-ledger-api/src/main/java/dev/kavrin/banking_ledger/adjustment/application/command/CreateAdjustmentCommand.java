package dev.kavrin.banking_ledger.adjustment.application.command;

import dev.kavrin.banking_ledger.adjustment.domain.model.AdjustmentReasonCode;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.ledger.application.command.PostingLineCommand;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import dev.kavrin.banking_ledger.transfer.domain.model.RequestedByActorType;

import java.util.List;

public record CreateAdjustmentCommand(
        String currencyCode,
        long amountMinor,

        AdjustmentReasonCode reasonCode,
        String reasonDetail,

        RequestedByActorType actorType,
        AuditActorRole actorRole,
        String actorId,
        String correlationId,

        List<PostingLineCommand> postingLines
) {

    public CreateAdjustmentCommand {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw badRequest("currencyCode is required");
        }
        if (reasonCode == null) {
            throw badRequest("reasonCode is required");
        }
        if (actorType == null) {
            throw badRequest("actorType is required");
        }
        if (actorRole == null) {
            throw badRequest("actorRole is required");
        }
        if (postingLines == null || postingLines.size() < 2) {
            throw badRequest("at least two posting lines are required");
        }

        currencyCode = currencyCode.trim().toUpperCase();

        if (!currencyCode.matches("^[A-Z]{3}$")) {
            throw badRequest("currencyCode must be a 3-letter uppercase ISO currency code");
        }

        if (amountMinor <= 0) {
            throw badRequest("amountMinor must be positive");
        }

        if (correlationId == null || correlationId.isBlank()) {
            throw badRequest("correlationId is required");
        }

        reasonDetail = normalizeNullable(reasonDetail);
        actorId = normalizeNullable(actorId);
        correlationId = correlationId.trim();
    }

    private static BadRequestException badRequest(String message) {
        return new BadRequestException(
                ApiErrorCode.Validation.INVALID_REQUEST,
                message,
                message
        );
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}