package dev.kavrin.banking_ledger.account.domain.model;

import dev.kavrin.banking_ledger.account.persistence.AccountEntity;

public record LockedTransferAccounts(
        AccountEntity sourceAccount,
        AccountEntity destinationAccount
) {
}