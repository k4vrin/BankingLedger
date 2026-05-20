package dev.kavrin.banking_ledger.transfer.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.kavrin.banking_ledger.account.application.service.LockedAccountLoader;
import dev.kavrin.banking_ledger.account.domain.model.AccountCategory;
import dev.kavrin.banking_ledger.account.domain.model.AccountStatus;
import dev.kavrin.banking_ledger.account.domain.model.AccountType;
import dev.kavrin.banking_ledger.account.domain.model.LockedTransferAccounts;
import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.idempotency.application.service.IdempotencyService;
import dev.kavrin.banking_ledger.idempotency.application.service.TransferRequestHasher;
import dev.kavrin.banking_ledger.idempotency.persistence.entity.IdempotencyRecordEntity;
import dev.kavrin.banking_ledger.ledger.application.command.PostLedgerTransactionCommand;
import dev.kavrin.banking_ledger.ledger.application.service.PostLedgerTransactionUseCase;
import dev.kavrin.banking_ledger.ledger.application.service.PostedLedgerTransactionResult;
import dev.kavrin.banking_ledger.ledger.application.service.PreloadedPostingAccounts;
import dev.kavrin.banking_ledger.ledger.domain.model.TransactionStatus;
import dev.kavrin.banking_ledger.ledger.persistence.entity.LedgerTransactionEntity;
import dev.kavrin.banking_ledger.ledger.persistence.repository.LedgerTransactionRepository;
import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;
import dev.kavrin.banking_ledger.transfer.application.command.CreateTransferCommand;
import dev.kavrin.banking_ledger.transfer.domain.model.RequestedByActorType;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestEntity;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateTransferUseCaseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void validTransferUsesLockedAccountLoaderAndPassesPreloadedAccountsToLedgerPosting() {
        var source = account(UUID.fromString("00000000-0000-0000-0000-000000000001"), 1_000);
        var destination = account(UUID.fromString("00000000-0000-0000-0000-000000000002"), 250);
        var lockedAccountLoader = new RecordingLockedAccountLoader(source, destination);
        var postLedgerTransactionUseCase = new RecordingPostLedgerTransactionUseCase();
        var transferRequestRepository = transferRequestRepository();
        var useCase = useCase(transferRequestRepository, postLedgerTransactionUseCase, lockedAccountLoader);

        var result = useCase.handle(validCommand(source.getId(), destination.getId()));

        assertThat(result.statusCode()).isEqualTo(201);
        assertThat(lockedAccountLoader.callCount).isEqualTo(1);
        assertThat(lockedAccountLoader.lastSourceAccountId).isEqualTo(source.getId());
        assertThat(lockedAccountLoader.lastDestinationAccountId).isEqualTo(destination.getId());
        assertThat(postLedgerTransactionUseCase.lastCommand).isNotNull();
        assertThat(postLedgerTransactionUseCase.lastPreloadedAccounts.accountsById())
                .containsOnlyKeys(source.getId(), destination.getId());
    }

    @Test
    void sameSourceAndDestinationIsRejectedBeforeLockingAccounts() {
        var accountId = UUID.randomUUID();
        var lockedAccountLoader = new RecordingLockedAccountLoader(account(accountId, 1_000), account(accountId, 1_000));
        var useCase = useCase(
                transferRequestRepository(),
                new RecordingPostLedgerTransactionUseCase(),
                lockedAccountLoader
        );

        assertThatThrownBy(() -> useCase.handle(validCommand(accountId, accountId)))
                .isInstanceOf(BadRequestException.class);

        assertThat(lockedAccountLoader.callCount).isZero();
    }

    private CreateTransferUseCase useCase(
            TransferRequestRepository transferRequestRepository,
            PostLedgerTransactionUseCase postLedgerTransactionUseCase,
            LockedAccountLoader lockedAccountLoader
    ) {
        return new CreateTransferUseCase(
                transferRequestRepository,
                ledgerTransactionRepository(),
                postLedgerTransactionUseCase,
                new NoopIdempotencyService(),
                new TransferRequestHasher(objectMapper),
                new TransferResponseMapper(),
                lockedAccountLoader,
                passthroughTransactionTemplate(),
                objectMapper
        );
    }

    private TransactionTemplate passthroughTransactionTemplate() {
        return new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
    }

    private CreateTransferCommand validCommand(UUID sourceAccountId, UUID destinationAccountId) {
        return new CreateTransferCommand(
                sourceAccountId,
                destinationAccountId,
                CurrencyCode.of("USD"),
                100,
                "unit-transfer-" + UUID.randomUUID(),
                "unit transfer",
                "unit-idem-" + UUID.randomUUID(),
                RequestedByActorType.SYSTEM,
                "corr-unit"
        );
    }

    private AccountEntity account(UUID accountId, long balanceMinor) {
        return AccountEntity.builder()
                .id(accountId)
                .accountNumber("ACC-" + accountId)
                .accountType(AccountType.CURRENT)
                .accountCategory(AccountCategory.CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .currencyCode("USD")
                .availableBalanceMinor(balanceMinor)
                .ledgerBalanceMinor(balanceMinor)
                .build();
    }

    private TransferRequestRepository transferRequestRepository() {
        return (TransferRequestRepository) Proxy.newProxyInstance(
                TransferRequestRepository.class.getClassLoader(),
                new Class<?>[]{TransferRequestRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByExternalReference" -> false;
                    case "save" -> {
                        var transfer = (TransferRequestEntity) args[0];
                        if (transfer.getId() == null) {
                            transfer.setId(UUID.randomUUID());
                        }
                        yield transfer;
                    }
                    case "flush" -> null;
                    case "toString" -> "RecordingTransferRequestRepository";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private LedgerTransactionRepository ledgerTransactionRepository() {
        return (LedgerTransactionRepository) Proxy.newProxyInstance(
                LedgerTransactionRepository.class.getClassLoader(),
                new Class<?>[]{LedgerTransactionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getReferenceById" -> LedgerTransactionEntity.builder()
                            .id((UUID) args[0])
                            .status(TransactionStatus.POSTED)
                            .currencyCode("USD")
                            .amountMinor(100)
                            .postedAt(OffsetDateTime.now())
                            .build();
                    case "toString" -> "RecordingLedgerTransactionRepository";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static class NoopIdempotencyService extends IdempotencyService {

        private NoopIdempotencyService() {
            super(null);
        }

        @Override
        public Optional<IdempotencyRecordEntity> findTransferCreate(String key) {
            return Optional.empty();
        }

        @Override
        public IdempotencyRecordEntity createTransferCreateRecord(
                String key,
                String requestHash,
                String responseBody,
                int statusCode,
                UUID transferId
        ) {
            return IdempotencyRecordEntity.builder()
                    .id(UUID.randomUUID())
                    .idempotencyKey(key)
                    .requestHash(requestHash)
                    .responseBody(responseBody)
                    .responseStatus(statusCode)
                    .resourceId(transferId)
                    .build();
        }
    }

    private static class RecordingLockedAccountLoader extends LockedAccountLoader {
        private final AccountEntity source;
        private final AccountEntity destination;
        private int callCount;
        private UUID lastSourceAccountId;
        private UUID lastDestinationAccountId;

        private RecordingLockedAccountLoader(AccountEntity source, AccountEntity destination) {
            super(null);
            this.source = source;
            this.destination = destination;
        }

        @Override
        public LockedTransferAccounts loadForTransfer(UUID sourceAccountId, UUID destinationAccountId) {
            callCount++;
            lastSourceAccountId = sourceAccountId;
            lastDestinationAccountId = destinationAccountId;
            return new LockedTransferAccounts(source, destination);
        }
    }

    private static class RecordingPostLedgerTransactionUseCase extends PostLedgerTransactionUseCase {
        private PostLedgerTransactionCommand lastCommand;
        private PreloadedPostingAccounts lastPreloadedAccounts;

        private RecordingPostLedgerTransactionUseCase() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public PostedLedgerTransactionResult handleWithPreloadedAccounts(
                PostLedgerTransactionCommand command,
                PreloadedPostingAccounts preloadedAccounts
        ) {
            lastCommand = command;
            lastPreloadedAccounts = preloadedAccounts;
            return new PostedLedgerTransactionResult(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    List.of(UUID.randomUUID(), UUID.randomUUID()),
                    OffsetDateTime.now()
            );
        }
    }
}
