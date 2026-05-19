package dev.kavrin.banking_ledger.transfer.domain.policy;

import dev.kavrin.banking_ledger.account.domain.policy.AccountStatusPolicy;
import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import dev.kavrin.banking_ledger.shared.error.BusinessRuleViolationException;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor
public final class TransferValidationPolicy {

    public static void validateRequest(UUID sourceAccountId, UUID destinationAccountId, long amountMinor) {
        if (sourceAccountId == null) {
            throw new BadRequestException(
                    ApiErrorCode.Validation.INVALID_REQUEST,
                    "Source account id is required",
                    "Source account id is required."
            );
        }

        if (destinationAccountId == null) {
            throw new BadRequestException(
                    ApiErrorCode.Validation.INVALID_REQUEST,
                    "Destination account id is required",
                    "Destination account id is required."
            );
        }

        if (sourceAccountId.equals(destinationAccountId)) {
            throw new BadRequestException(
                    ApiErrorCode.Validation.INVALID_REQUEST,
                    "Source and destination accounts must be different",
                    "Source and destination accounts must be different."
            );
        }

        if (amountMinor <= 0) {
            throw new BadRequestException(
                    ApiErrorCode.Validation.INVALID_REQUEST,
                    "Transfer amount must be positive",
                    "Transfer amount must be positive."
            );
        }
    }

    public static void validateAccounts(
            AccountEntity source,
            AccountEntity destination,
            CurrencyCode currencyCode,
            long amountMinor
    ) {
        AccountStatusPolicy.validateCanDebit(source.getStatus());
        AccountStatusPolicy.validateCanCredit(destination.getStatus());

        validateCurrency(source, currencyCode, "Source");
        validateCurrency(destination, currencyCode, "Destination");

        if (source.getAvailableBalanceMinor() < amountMinor) {
            throw new BusinessRuleViolationException(
                    ApiErrorCode.Business.INSUFFICIENT_FUNDS,
                    "Source account has insufficient available funds: " + source.getId(),
                    "Source account has insufficient available funds."
            );
        }
    }

    private static void validateCurrency(AccountEntity account, CurrencyCode currencyCode, String accountLabel) {
        if (!account.getCurrencyCode().equals(currencyCode.value())) {
            throw new BusinessRuleViolationException(
                    ApiErrorCode.Business.POSTING_ACCOUNT_CURRENCY_MISMATCH,
                    accountLabel + " account currency " + account.getCurrencyCode()
                            + " does not match transfer currency " + currencyCode.value(),
                    accountLabel + " account currency must match transfer currency."
            );
        }
    }
}
