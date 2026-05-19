package dev.kavrin.banking_ledger.account.application.service;

import dev.kavrin.banking_ledger.account.domain.model.LockedTransferAccounts;
import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.account.persistence.AccountRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class LockedAccountLoader {

    private final AccountRepository accountRepository;

    public LockedTransferAccounts loadForTransfer(
            UUID sourceAccountId,
            UUID destinationAccountId
    ) {

        if (sourceAccountId.equals(destinationAccountId)) {
            var accountEntity = findLocked(sourceAccountId);
            return new LockedTransferAccounts(accountEntity, accountEntity);
        }

        var orderedIds = Stream.of(sourceAccountId, destinationAccountId)
                .sorted()
                .toList();

        var lockedAccounts = new HashMap<UUID, AccountEntity>();

        for (UUID accountId : orderedIds) {
            lockedAccounts.put(accountId, findLocked(accountId));
        }

        return new LockedTransferAccounts(
                lockedAccounts.get(sourceAccountId),
                lockedAccounts.get(destinationAccountId)
        );

    }

    private AccountEntity findLocked(UUID accountId) {

        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ApiErrorCode.Business.ACCOUNT_NOT_FOUND,
                        "Account not found with id: " + accountId,
                        "Account not found."
                ));
    }

}
