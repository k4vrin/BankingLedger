package dev.kavrin.banking_ledger.account.domain.policy;

import dev.kavrin.banking_ledger.account.domain.model.AccountStatus;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BusinessRuleViolationException;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class AccountStatusPolicy {

    public static boolean canDebit(AccountStatus accountStatus) {
        return switch (accountStatus) {
            case ACTIVE -> true;
            case FROZEN, CLOSED -> false;
        };
    }

    public static boolean canCredit(AccountStatus accountStatus) {
        return switch (accountStatus) {
            case ACTIVE, FROZEN -> true;
            case CLOSED -> false;
        };
    }

    public static void validateCanDebit(AccountStatus accountStatus) {
        if (!canDebit(accountStatus)) {
            throw new BusinessRuleViolationException(
                    ApiErrorCode.Business.INVALID_ACCOUNT_STATUS,
                    "Account cannot be debited when status is " + accountStatus,
                    "Account cannot be debited in its current status."
            );
        }
    }

    public static void validateCanCredit(AccountStatus accountStatus) {
        if (!canCredit(accountStatus)) {
            throw new BusinessRuleViolationException(
                    ApiErrorCode.Business.INVALID_ACCOUNT_STATUS,
                    "Account cannot be credited when status is " + accountStatus,
                    "Account cannot be credited in its current status."
            );
        }
    }
}
