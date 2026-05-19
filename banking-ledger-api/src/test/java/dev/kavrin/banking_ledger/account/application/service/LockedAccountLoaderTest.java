package dev.kavrin.banking_ledger.account.application.service;

import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.account.persistence.AccountRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LockedAccountLoaderTest {

    @Test
    void locksTransferAccountsInDeterministicIdOrderAndPreservesRoles() {
        var higherSourceId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var lowerDestinationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var source = AccountEntity.builder().id(higherSourceId).build();
        var destination = AccountEntity.builder().id(lowerDestinationId).build();
        var lockedIds = new ArrayList<UUID>();
        var accountRepository = recordingRepository(
                Map.of(
                        higherSourceId, source,
                        lowerDestinationId, destination
                ),
                lockedIds
        );
        var lockedAccountLoader = new LockedAccountLoader(accountRepository);

        var accounts = lockedAccountLoader.loadForTransfer(higherSourceId, lowerDestinationId);

        assertThat(accounts.sourceAccount()).isSameAs(source);
        assertThat(accounts.destinationAccount()).isSameAs(destination);
        assertThat(lockedIds).containsExactly(lowerDestinationId, higherSourceId);
    }

    private static AccountRepository recordingRepository(
            Map<UUID, AccountEntity> accountsById,
            List<UUID> lockedIds
    ) {
        return (AccountRepository) Proxy.newProxyInstance(
                AccountRepository.class.getClassLoader(),
                new Class<?>[]{AccountRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByIdForUpdate")) {
                        var accountId = (UUID) args[0];
                        lockedIds.add(accountId);
                        return Optional.ofNullable(accountsById.get(accountId));
                    }

                    if (method.getName().equals("toString")) {
                        return "RecordingAccountRepository";
                    }

                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
