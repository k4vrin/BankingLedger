package dev.kavrin.banking_ledger.ledger.application.service;

import dev.kavrin.banking_ledger.account.domain.model.AccountCategory;
import dev.kavrin.banking_ledger.account.domain.policy.AccountStatusPolicy;
import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.ledger.domain.model.Posting;
import dev.kavrin.banking_ledger.ledger.domain.model.PostingDirection;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BusinessRuleViolationException;
import org.springframework.stereotype.Component;

@Component
class AccountBalanceUpdater {

    void apply(AccountEntity account, Posting posting) {
        if (account.getAccountCategory() != AccountCategory.CUSTOMER) {
            return;
        }

        if (posting.direction() == PostingDirection.DEBIT) {
            AccountStatusPolicy.validateCanDebit(account.getStatus());
            debit(account, posting.amountMinor());
            return;
        }

        AccountStatusPolicy.validateCanCredit(account.getStatus());
        credit(account, posting.amountMinor());
    }

    private void debit(AccountEntity account, long amountMinor) {
        if (account.getAvailableBalanceMinor() < amountMinor || account.getLedgerBalanceMinor() < amountMinor) {
            throw new BusinessRuleViolationException(
                    ApiErrorCode.Business.INSUFFICIENT_FUNDS,
                    "Account has insufficient funds: " + account.getId(),
                    "Account has insufficient funds."
            );
        }

        account.setAvailableBalanceMinor(Math.subtractExact(account.getAvailableBalanceMinor(), amountMinor));
        account.setLedgerBalanceMinor(Math.subtractExact(account.getLedgerBalanceMinor(), amountMinor));
    }

    private void credit(AccountEntity account, long amountMinor) {
        account.setAvailableBalanceMinor(Math.addExact(account.getAvailableBalanceMinor(), amountMinor));
        account.setLedgerBalanceMinor(Math.addExact(account.getLedgerBalanceMinor(), amountMinor));
    }
}
