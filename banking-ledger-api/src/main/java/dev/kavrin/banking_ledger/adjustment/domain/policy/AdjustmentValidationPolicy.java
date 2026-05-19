package dev.kavrin.banking_ledger.adjustment.domain.policy;

import dev.kavrin.banking_ledger.account.domain.policy.AccountStatusPolicy;
import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.adjustment.application.command.CreateAdjustmentCommand;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.ledger.application.command.PostingLineCommand;
import dev.kavrin.banking_ledger.ledger.domain.model.PostingDirection;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import dev.kavrin.banking_ledger.shared.error.SecurityDomainException;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdjustmentValidationPolicy {

    private AdjustmentValidationPolicy() {
    }

    public static void validateRequest(CreateAdjustmentCommand command) {
        if (command == null) {
            throw invalidRequest("command is required");
        }

        if (command.reasonCode() == null) {
            throw invalidRequest("reasonCode is required");
        }

        if (command.currencyCode() == null || command.currencyCode().isBlank()) {
            throw invalidRequest("currencyCode is required");
        }

        if (command.amountMinor() <= 0) {
            throw invalidRequest("amountMinor must be positive");
        }

        if (command.postingLines() == null || command.postingLines().size() < 2) {
            throw invalidRequest("adjustment requires at least two posting lines");
        }

        if (command.actorRole() != AuditActorRole.OPS_ADMIN && command.actorRole() != AuditActorRole.SERVICE) {
            throw SecurityDomainException.forbidden(
                    ApiErrorCode.Security.FORBIDDEN_RESOURCE,
                    "Actor role is not allowed to post adjustments: " + command.actorRole(),
                    "Actor role is not allowed to post adjustments."
            );
        }

        validateBalancedPostings(command.postingLines());
    }

    public static void validateAccounts(
            CreateAdjustmentCommand command,
            Map<UUID, AccountEntity> accountsById
    ) {
        CurrencyCode adjustmentCurrency = CurrencyCode.of(command.currencyCode());

        for (PostingLineCommand line : command.postingLines()) {
            AccountEntity account = accountsById.get(line.accountId());

            if (account == null) {
                throw invalidRequest("posting account not found: " + line.accountId());
            }

            if (!account.getCurrencyCode().equals(adjustmentCurrency.value())) {
                throw invalidRequest("posting account currency mismatch");
            }

            if (line.direction() == PostingDirection.DEBIT) {
                validateCanDebit(account, line.amountMinor());
            }

            if (line.direction() == PostingDirection.CREDIT) {
                validateCanCredit(account);
            }
        }
    }

    private static void validateBalancedPostings(List<PostingLineCommand> lines) {
        long totalDebit = 0;
        long totalCredit = 0;

        boolean hasDebit = false;
        boolean hasCredit = false;

        for (PostingLineCommand line : lines) {
            if (line.accountId() == null) {
                throw invalidRequest("accountId is required");
            }

            if (line.direction() == null) {
                throw invalidRequest("direction is required");
            }

            if (line.amountMinor() <= 0) {
                throw invalidRequest("posting amount must be positive");
            }

            if (line.direction() == PostingDirection.DEBIT) {
                hasDebit = true;
                totalDebit = Math.addExact(totalDebit, line.amountMinor());
            } else if (line.direction() == PostingDirection.CREDIT) {
                hasCredit = true;
                totalCredit = Math.addExact(totalCredit, line.amountMinor());
            }
        }

        if (!hasDebit || !hasCredit) {
            throw invalidRequest("adjustment requires at least one debit and one credit");
        }

        if (totalDebit != totalCredit) {
            throw invalidRequest("adjustment debit total must equal credit total");
        }
    }

    private static void validateCanDebit(AccountEntity account, long amountMinor) {
        if (!AccountStatusPolicy.canDebit(account.getStatus())) {
            throw invalidRequest("account cannot be debited");
        }

        if (account.getAvailableBalanceMinor() < amountMinor) {
            throw invalidRequest("insufficient funds");
        }
    }

    private static void validateCanCredit(AccountEntity account) {
        if (!AccountStatusPolicy.canCredit(account.getStatus())) {
            throw invalidRequest("account cannot be credited");
        }
    }

    private static BadRequestException invalidRequest(String message) {
        return new BadRequestException(
                ApiErrorCode.Validation.INVALID_REQUEST,
                message,
                "Adjustment request is invalid."
        );
    }
}
