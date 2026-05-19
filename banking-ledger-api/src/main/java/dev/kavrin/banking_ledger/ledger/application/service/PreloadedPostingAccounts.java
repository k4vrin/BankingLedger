package dev.kavrin.banking_ledger.ledger.application.service;

import dev.kavrin.banking_ledger.account.persistence.AccountEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record PreloadedPostingAccounts(Map<UUID, AccountEntity> accountsById) {

    public PreloadedPostingAccounts {
        accountsById = Map.copyOf(accountsById);
    }

    public static PreloadedPostingAccounts from(AccountEntity... accounts) {
        var accountsById = new HashMap<UUID, AccountEntity>();

        for (var account : accounts) {
            if (account == null || account.getId() == null) {
                throw new IllegalArgumentException("Preloaded posting accounts must have ids.");
            }
            accountsById.put(account.getId(), account);
        }

        return new PreloadedPostingAccounts(accountsById);
    }
}
