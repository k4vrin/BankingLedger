package dev.kavrin.banking_ledger.transfer.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.account.domain.model.AccountCategory;
import dev.kavrin.banking_ledger.account.domain.model.AccountStatus;
import dev.kavrin.banking_ledger.account.domain.model.AccountType;
import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.account.persistence.AccountRepository;
import dev.kavrin.banking_ledger.customer.domain.model.CustomerStatus;
import dev.kavrin.banking_ledger.customer.persistence.CustomerEntity;
import dev.kavrin.banking_ledger.customer.persistence.CustomerRepository;
import dev.kavrin.banking_ledger.idempotency.persistence.repository.IdempotencyRecordRepository;
import dev.kavrin.banking_ledger.ledger.persistence.repository.LedgerTransactionRepository;
import dev.kavrin.banking_ledger.ledger.persistence.repository.PostingRepository;
import dev.kavrin.banking_ledger.shared.error.*;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;
import dev.kavrin.banking_ledger.transfer.application.command.CreateTransferCommand;
import dev.kavrin.banking_ledger.transfer.domain.model.RequestedByActorType;
import dev.kavrin.banking_ledger.transfer.domain.model.TransferStatus;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CreateTransferUseCaseIntegrationTests {

    @Autowired
    private CreateTransferUseCase createTransferUseCase;

    @Autowired
    private TransferQueryUseCase transferQueryUseCase;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRequestRepository transferRequestRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private PostingRepository postingRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void successfulTransferPersistsCompletedTransferLedgerPostingsBalancesAndIdempotencyResponse() throws Exception {
        var source = createAccount("SRC", "USD", AccountStatus.ACTIVE, 1_000);
        var destination = createAccount("DST", "USD", AccountStatus.ACTIVE, 250);
        var command = validCommand(source.getId(), destination.getId(), "transfer-success", "idem-success");

        var result = createTransferUseCase.handle(command);

        assertThat(result.statusCode()).isEqualTo(201);
        assertThat(result.replayed()).isFalse();
        assertThat(result.transferId()).isNotNull();

        var response = objectMapper.readTree(result.responseBody());
        assertThat(response.get("id").asText()).isEqualTo(result.transferId().toString());
        assertThat(response.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(response.get("ledgerTransactionId").asText()).isNotBlank();

        var transfer = transferRequestRepository.findById(result.transferId()).orElseThrow();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.getLedgerTransaction()).isNotNull();
        assertThat(ledgerTransactionRepository.existsById(transfer.getLedgerTransaction().getId())).isTrue();
        assertThat(postingRepository.countByJournalEntry_LedgerTransaction_Id(transfer.getLedgerTransaction().getId())).isEqualTo(2);

        var updatedSource = accountRepository.findById(source.getId()).orElseThrow();
        var updatedDestination = accountRepository.findById(destination.getId()).orElseThrow();
        assertThat(updatedSource.getAvailableBalanceMinor()).isEqualTo(900);
        assertThat(updatedSource.getLedgerBalanceMinor()).isEqualTo(900);
        assertThat(updatedDestination.getAvailableBalanceMinor()).isEqualTo(350);
        assertThat(updatedDestination.getLedgerBalanceMinor()).isEqualTo(350);

        var idempotencyRecord = idempotencyRecordRepository
                .findByOperationScopeAndIdempotencyKey("TRANSFER_CREATE", "idem-success")
                .orElseThrow();
        assertThat(idempotencyRecord.getRequestHash()).isNotBlank();
        assertThat(idempotencyRecord.getResourceType()).isEqualTo("TRANSFER");
        assertThat(idempotencyRecord.getResourceId()).isEqualTo(result.transferId());
        assertThat(idempotencyRecord.getResponseStatus()).isEqualTo(201);
        assertThat(idempotencyRecord.getResponseBody()).isEqualTo(result.responseBody());

        var lookupResponse = transferQueryUseCase.getById(new dev.kavrin.banking_ledger.transfer.application.query.GetTransferByIdQuery(result.transferId()));
        assertThat(lookupResponse.id()).isEqualTo(result.transferId());
        assertThat(lookupResponse.ledgerTransactionId()).isEqualTo(transfer.getLedgerTransaction().getId());
    }

    @Test
    void duplicateIdempotencyKeyWithSameRequestReplaysOriginalResponseWithoutDuplicatePostings() {
        var source = createAccount("SRC", "USD", AccountStatus.ACTIVE, 1_000);
        var destination = createAccount("DST", "USD", AccountStatus.ACTIVE, 250);
        var command = validCommand(source.getId(), destination.getId(), "transfer-replay", "idem-replay");

        var first = createTransferUseCase.handle(command);
        var second = createTransferUseCase.handle(command);

        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(second.replayed()).isTrue();
        assertThat(second.transferId()).isEqualTo(first.transferId());
        assertThat(second.responseBody()).isEqualTo(first.responseBody());
        assertThat(countTransfersByExternalReference("transfer-replay")).isEqualTo(1);

        var transfer = transferRequestRepository.findByExternalReference("transfer-replay").orElseThrow();
        assertThat(postingRepository.countByJournalEntry_LedgerTransaction_Id(transfer.getLedgerTransaction().getId())).isEqualTo(2);
    }

    @Test
    void duplicateIdempotencyKeyWithDifferentRequestIsRejected() {
        var source = createAccount("SRC", "USD", AccountStatus.ACTIVE, 1_000);
        var destination = createAccount("DST", "USD", AccountStatus.ACTIVE, 250);
        createTransferUseCase.handle(validCommand(source.getId(), destination.getId(), "transfer-conflict-1", "idem-conflict"));

        assertThatThrownBy(() -> createTransferUseCase.handle(validCommand(
                source.getId(),
                destination.getId(),
                "transfer-conflict-2",
                "idem-conflict"
        )))
                .isInstanceOfSatisfying(ConflictException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.INVALID_IDEMPOTENCY_KEY));
    }

    @Test
    void duplicateExternalReferenceIsRejected() {
        var source = createAccount("SRC", "USD", AccountStatus.ACTIVE, 1_000);
        var destination = createAccount("DST", "USD", AccountStatus.ACTIVE, 250);
        createTransferUseCase.handle(validCommand(source.getId(), destination.getId(), "duplicate-transfer", "idem-dup-1"));

        assertThatThrownBy(() -> createTransferUseCase.handle(validCommand(
                source.getId(),
                destination.getId(),
                "duplicate-transfer",
                "idem-dup-2"
        )))
                .isInstanceOfSatisfying(ConflictException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.DUPLICATE_REQUEST));
    }

    @Test
    void missingSourceAndDestinationAccountsAreRejected() {
        var destination = createAccount("DST", "USD", AccountStatus.ACTIVE, 250);

        assertThatThrownBy(() -> createTransferUseCase.handle(validCommand(
                UUID.randomUUID(),
                destination.getId(),
                "missing-source",
                "idem-missing-source"
        )))
                .isInstanceOf(ResourceNotFoundException.class);

        var source = createAccount("SRC", "USD", AccountStatus.ACTIVE, 1_000);
        assertThatThrownBy(() -> createTransferUseCase.handle(validCommand(
                source.getId(),
                UUID.randomUUID(),
                "missing-destination",
                "idem-missing-destination"
        )))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void sameSourceAndDestinationAccountIsRejected() {
        var account = createAccount("SAME", "USD", AccountStatus.ACTIVE, 1_000);

        assertThatThrownBy(() -> createTransferUseCase.handle(validCommand(
                account.getId(),
                account.getId(),
                "same-account",
                "idem-same-account"
        )))
                .isInstanceOfSatisfying(BadRequestException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Validation.INVALID_REQUEST));
    }

    @Test
    void statusCurrencyAndFundsValidationFailuresAreRejectedBeforePosting() {
        var activeSource = createAccount("SRC", "USD", AccountStatus.ACTIVE, 50);
        var frozenSource = createAccount("FRZ", "USD", AccountStatus.FROZEN, 1_000);
        var activeDestination = createAccount("DST", "USD", AccountStatus.ACTIVE, 250);
        var closedDestination = createAccount("CLS", "USD", AccountStatus.CLOSED, 250);
        var eurDestination = createAccount("EUR", "EUR", AccountStatus.ACTIVE, 250);

        assertThatThrownBy(() -> createTransferUseCase.handle(validCommand(
                frozenSource.getId(),
                activeDestination.getId(),
                "frozen-source",
                "idem-frozen-source"
        )))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.INVALID_ACCOUNT_STATUS));

        assertThatThrownBy(() -> createTransferUseCase.handle(validCommand(
                activeSource.getId(),
                closedDestination.getId(),
                "closed-destination",
                "idem-closed-destination"
        )))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.INVALID_ACCOUNT_STATUS));

        assertThatThrownBy(() -> createTransferUseCase.handle(validCommand(
                activeSource.getId(),
                eurDestination.getId(),
                "currency-mismatch",
                "idem-currency-mismatch"
        )))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.POSTING_ACCOUNT_CURRENCY_MISMATCH));

        assertThatThrownBy(() -> createTransferUseCase.handle(validCommand(
                activeSource.getId(),
                activeDestination.getId(),
                "insufficient-funds",
                "idem-insufficient-funds"
        )))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.INSUFFICIENT_FUNDS));

        assertThat(countTransfersByExternalReference("frozen-source")).isZero();
        assertThat(countTransfersByExternalReference("closed-destination")).isZero();
        assertThat(countTransfersByExternalReference("currency-mismatch")).isZero();
        assertThat(countTransfersByExternalReference("insufficient-funds")).isZero();
    }

    @Test
    void ledgerPostingFailureRollsBackTransferRequest() {
        var source = createAccount("SRC", "USD", AccountStatus.ACTIVE, 1_000);
        var destination = createAccount("DST", "USD", AccountStatus.ACTIVE, 250);
        var externalReference = "ledger-duplicate-" + shortSuffix();
        insertPostedLedgerTransaction(externalReference);

        assertThatThrownBy(() -> createTransferUseCase.handle(validCommand(
                source.getId(),
                destination.getId(),
                externalReference,
                "idem-ledger-duplicate"
        )))
                .isInstanceOfSatisfying(ConflictException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.LEDGER_TRANSACTION_ALREADY_EXISTS));

        TestTransaction.flagForRollback();
        TestTransaction.end();
        TestTransaction.start();

        assertThat(transferRequestRepository.findByExternalReference(externalReference)).isEmpty();
        assertThat(idempotencyRecordRepository.findByOperationScopeAndIdempotencyKey("TRANSFER_CREATE", "idem-ledger-duplicate")).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentTransfersFromSameSourceDoNotOverdraftAndOnlyAvailableFundsComplete() throws Exception {
        var run = shortSuffix();
        var externalReferencePrefix = "concurrent-transfer-" + run + "-";
        var idempotencyPrefix = "idem-concurrent-" + run + "-";
        var source = transactionTemplate.execute(status ->
                createAccount("SRC-CON", "USD", AccountStatus.ACTIVE, 300));
        var destination = transactionTemplate.execute(status ->
                createAccount("DST-CON", "USD", AccountStatus.ACTIVE, 0));

        var executor = Executors.newFixedThreadPool(6);
        var start = new CountDownLatch(1);
        var results = Collections.synchronizedList(new ArrayList<CreateTransferResult>());
        var failures = Collections.synchronizedList(new ArrayList<Throwable>());
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                var externalReference = externalReferencePrefix + index;
                var idempotencyKey = idempotencyPrefix + index;
                tasks.add(() -> {
                    await(start);
                    try {
                        results.add(createTransferUseCase.handle(validCommand(
                                source.getId(),
                                destination.getId(),
                                externalReference,
                                idempotencyKey
                        )));
                    } catch (Throwable exception) {
                        failures.add(exception);
                    }
                    return null;
                });
            }

            var futures = tasks.stream()
                    .map(executor::submit)
                    .toList();
            start.countDown();
            for (var future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }

            assertThat(results).hasSize(3);
            assertThat(failures).hasSize(3);
            assertThat(failures).allSatisfy(exception ->
                    assertThat(exception).isInstanceOfSatisfying(BusinessRuleViolationException.class, businessException ->
                            assertThat(businessException.code()).isEqualTo(ApiErrorCode.Business.INSUFFICIENT_FUNDS)));

            var updatedSource = accountRepository.findById(source.getId()).orElseThrow();
            var updatedDestination = accountRepository.findById(destination.getId()).orElseThrow();
            assertThat(updatedSource.getAvailableBalanceMinor()).isZero();
            assertThat(updatedSource.getLedgerBalanceMinor()).isZero();
            assertThat(updatedDestination.getAvailableBalanceMinor()).isEqualTo(300);
            assertThat(updatedDestination.getLedgerBalanceMinor()).isEqualTo(300);
            assertThat(countTransfersByExternalReferencePrefix(externalReferencePrefix)).isEqualTo(3);
        } finally {
            executor.shutdownNow();
            cleanupTransferRun(
                    externalReferencePrefix,
                    idempotencyPrefix,
                    source.getId(),
                    destination.getId(),
                    source.getCustomer().getId(),
                    destination.getCustomer().getId()
            );
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentIndependentTransfersCompleteWithoutChangingUnrelatedAccounts() throws Exception {
        var run = shortSuffix();
        var externalReferencePrefix = "concurrent-independent-" + run + "-";
        var idempotencyPrefix = "idem-independent-" + run + "-";
        var sourceOne = transactionTemplate.execute(status ->
                createAccount("SRC-I1", "USD", AccountStatus.ACTIVE, 500));
        var destinationOne = transactionTemplate.execute(status ->
                createAccount("DST-I1", "USD", AccountStatus.ACTIVE, 10));
        var sourceTwo = transactionTemplate.execute(status ->
                createAccount("SRC-I2", "USD", AccountStatus.ACTIVE, 700));
        var destinationTwo = transactionTemplate.execute(status ->
                createAccount("DST-I2", "USD", AccountStatus.ACTIVE, 20));
        var untouched = transactionTemplate.execute(status ->
                createAccount("UNTOUCHED", "USD", AccountStatus.ACTIVE, 900));

        try {
            var runResult = runConcurrently(List.of(
                    () -> createTransferUseCase.handle(validCommand(
                            sourceOne.getId(),
                            destinationOne.getId(),
                            externalReferencePrefix + "0",
                            idempotencyPrefix + "0"
                    )),
                    () -> createTransferUseCase.handle(validCommand(
                            sourceTwo.getId(),
                            destinationTwo.getId(),
                            externalReferencePrefix + "1",
                            idempotencyPrefix + "1"
                    ))
            ));

            assertThat(runResult.results()).hasSize(2);
            assertThat(runResult.failures()).isEmpty();
            assertBalances(sourceOne.getId(), 400, 400);
            assertBalances(destinationOne.getId(), 110, 110);
            assertBalances(sourceTwo.getId(), 600, 600);
            assertBalances(destinationTwo.getId(), 120, 120);
            assertBalances(untouched.getId(), 900, 900);
            assertThat(countTransfersByExternalReferencePrefix(externalReferencePrefix)).isEqualTo(2);
            assertThat(countLedgerTransactionsByExternalReferencePrefix(externalReferencePrefix)).isEqualTo(2);
            assertThat(countPostingsByLedgerExternalReferencePrefix(externalReferencePrefix)).isEqualTo(4);
        } finally {
            cleanupTransferRun(externalReferencePrefix, idempotencyPrefix, List.of(sourceOne, destinationOne, sourceTwo, destinationTwo, untouched));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentTransfersFromSameSourceWithinAvailableBalanceAllComplete() throws Exception {
        var run = shortSuffix();
        var externalReferencePrefix = "concurrent-within-" + run + "-";
        var idempotencyPrefix = "idem-within-" + run + "-";
        var source = transactionTemplate.execute(status ->
                createAccount("SRC-WITHIN", "USD", AccountStatus.ACTIVE, 500));
        var destination = transactionTemplate.execute(status ->
                createAccount("DST-WITHIN", "USD", AccountStatus.ACTIVE, 25));

        try {
            var runResult = runConcurrently(List.of(
                    () -> createTransferUseCase.handle(validCommand(source.getId(), destination.getId(), externalReferencePrefix + "0", idempotencyPrefix + "0")),
                    () -> createTransferUseCase.handle(validCommand(source.getId(), destination.getId(), externalReferencePrefix + "1", idempotencyPrefix + "1")),
                    () -> createTransferUseCase.handle(validCommand(source.getId(), destination.getId(), externalReferencePrefix + "2", idempotencyPrefix + "2"))
            ));

            assertThat(runResult.results()).hasSize(3);
            assertThat(runResult.failures()).isEmpty();
            assertBalances(source.getId(), 200, 200);
            assertBalances(destination.getId(), 325, 325);
            assertThat(countTransfersByExternalReferencePrefix(externalReferencePrefix)).isEqualTo(3);
            assertThat(countLedgerTransactionsByExternalReferencePrefix(externalReferencePrefix)).isEqualTo(3);
            assertThat(countPostingsByLedgerExternalReferencePrefix(externalReferencePrefix)).isEqualTo(6);
        } finally {
            cleanupTransferRun(externalReferencePrefix, idempotencyPrefix, List.of(source, destination));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentTransfersBetweenSameTwoAccountsCompleteWithoutDeadlock() throws Exception {
        var run = shortSuffix();
        var externalReferencePrefix = "concurrent-same-pair-" + run + "-";
        var idempotencyPrefix = "idem-same-pair-" + run + "-";
        var source = transactionTemplate.execute(status ->
                createAccount("SRC-PAIR", "USD", AccountStatus.ACTIVE, 400));
        var destination = transactionTemplate.execute(status ->
                createAccount("DST-PAIR", "USD", AccountStatus.ACTIVE, 0));

        try {
            var runResult = runConcurrently(List.of(
                    () -> createTransferUseCase.handle(validCommand(source.getId(), destination.getId(), externalReferencePrefix + "0", idempotencyPrefix + "0")),
                    () -> createTransferUseCase.handle(validCommand(source.getId(), destination.getId(), externalReferencePrefix + "1", idempotencyPrefix + "1")),
                    () -> createTransferUseCase.handle(validCommand(source.getId(), destination.getId(), externalReferencePrefix + "2", idempotencyPrefix + "2"))
            ));

            assertThat(runResult.results()).hasSize(3);
            assertThat(runResult.failures()).isEmpty();
            assertBalances(source.getId(), 100, 100);
            assertBalances(destination.getId(), 300, 300);
            assertThat(countPostingsByLedgerExternalReferencePrefix(externalReferencePrefix)).isEqualTo(6);
        } finally {
            cleanupTransferRun(externalReferencePrefix, idempotencyPrefix, List.of(source, destination));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCrossTransfersBetweenTwoAccountsCompleteWithoutDeadlock() throws Exception {
        var run = shortSuffix();
        var externalReferencePrefix = "concurrent-cross-" + run + "-";
        var idempotencyPrefix = "idem-cross-" + run + "-";
        var accountA = transactionTemplate.execute(status ->
                createAccount("SRC-CROSS-A", "USD", AccountStatus.ACTIVE, 500));
        var accountB = transactionTemplate.execute(status ->
                createAccount("SRC-CROSS-B", "USD", AccountStatus.ACTIVE, 500));

        try {
            var runResult = runConcurrently(List.of(
                    () -> createTransferUseCase.handle(validCommand(accountA.getId(), accountB.getId(), externalReferencePrefix + "a-to-b-0", idempotencyPrefix + "0")),
                    () -> createTransferUseCase.handle(validCommand(accountB.getId(), accountA.getId(), externalReferencePrefix + "b-to-a-0", idempotencyPrefix + "1")),
                    () -> createTransferUseCase.handle(validCommand(accountA.getId(), accountB.getId(), externalReferencePrefix + "a-to-b-1", idempotencyPrefix + "2")),
                    () -> createTransferUseCase.handle(validCommand(accountB.getId(), accountA.getId(), externalReferencePrefix + "b-to-a-1", idempotencyPrefix + "3"))
            ));

            assertThat(runResult.results()).hasSize(4);
            assertThat(runResult.failures()).isEmpty();
            assertBalances(accountA.getId(), 500, 500);
            assertBalances(accountB.getId(), 500, 500);
            assertThat(countTransfersByExternalReferencePrefix(externalReferencePrefix)).isEqualTo(4);
            assertThat(countPostingsByLedgerExternalReferencePrefix(externalReferencePrefix)).isEqualTo(8);
        } finally {
            cleanupTransferRun(externalReferencePrefix, idempotencyPrefix, List.of(accountA, accountB));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDuplicateIdempotencyRequestsWithSamePayloadCreateOneTransferAndReplayOneResponse() throws Exception {
        var run = shortSuffix();
        var externalReferencePrefix = "concurrent-same-idem-" + run + "-";
        var idempotencyPrefix = "idem-same-key-" + run + "-";
        var source = transactionTemplate.execute(status ->
                createAccount("SRC-IDEM", "USD", AccountStatus.ACTIVE, 500));
        var destination = transactionTemplate.execute(status ->
                createAccount("DST-IDEM", "USD", AccountStatus.ACTIVE, 0));

        try {
            var command = validCommand(source.getId(), destination.getId(), externalReferencePrefix + "same", idempotencyPrefix + "same");
            var runResult = runConcurrently(List.of(
                    () -> createTransferUseCase.handle(command),
                    () -> createTransferUseCase.handle(command),
                    () -> createTransferUseCase.handle(command)
            ));

            assertThat(runResult.failures()).isEmpty();
            assertThat(runResult.results()).hasSize(3);
            assertThat(runResult.results().stream().map(CreateTransferResult::transferId).distinct()).hasSize(1);
            assertThat(runResult.results().stream().map(CreateTransferResult::responseBody).distinct()).hasSize(1);
            assertThat(runResult.results().stream().map(CreateTransferResult::statusCode)).contains(201, 200, 200);
            assertBalances(source.getId(), 400, 400);
            assertBalances(destination.getId(), 100, 100);
            assertThat(countTransfersByExternalReferencePrefix(externalReferencePrefix)).isEqualTo(1);
            assertThat(countLedgerTransactionsByExternalReferencePrefix(externalReferencePrefix)).isEqualTo(1);
            assertThat(countPostingsByLedgerExternalReferencePrefix(externalReferencePrefix)).isEqualTo(2);
            assertThat(countIdempotencyRecordsByKeyPrefix(idempotencyPrefix)).isEqualTo(1);
        } finally {
            cleanupTransferRun(externalReferencePrefix, idempotencyPrefix, List.of(source, destination));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDuplicateIdempotencyRequestsWithDifferentPayloadsRejectConflictsWithoutDuplicatePostings() throws Exception {
        var run = shortSuffix();
        var externalReferencePrefix = "concurrent-diff-idem-" + run + "-";
        var idempotencyPrefix = "idem-diff-key-" + run + "-";
        var source = transactionTemplate.execute(status ->
                createAccount("SRC-IDEM-DIFF", "USD", AccountStatus.ACTIVE, 500));
        var destination = transactionTemplate.execute(status ->
                createAccount("DST-IDEM-DIFF", "USD", AccountStatus.ACTIVE, 0));

        try {
            var runResult = runConcurrently(List.of(
                    () -> createTransferUseCase.handle(validCommand(source.getId(), destination.getId(), externalReferencePrefix + "0", idempotencyPrefix + "same")),
                    () -> createTransferUseCase.handle(validCommand(source.getId(), destination.getId(), externalReferencePrefix + "1", idempotencyPrefix + "same")),
                    () -> createTransferUseCase.handle(validCommand(source.getId(), destination.getId(), externalReferencePrefix + "2", idempotencyPrefix + "same"))
            ));

            assertThat(runResult.results()).hasSize(1);
            assertThat(runResult.failures()).hasSize(2);
            assertThat(runResult.failures()).allSatisfy(exception ->
                    assertThat(exception).isInstanceOfSatisfying(ConflictException.class, conflictException ->
                            assertThat(conflictException.code()).isEqualTo(ApiErrorCode.Business.INVALID_IDEMPOTENCY_KEY)));
            assertBalances(source.getId(), 400, 400);
            assertBalances(destination.getId(), 100, 100);
            assertThat(countTransfersByExternalReferencePrefix(externalReferencePrefix)).isEqualTo(1);
            assertThat(countLedgerTransactionsByExternalReferencePrefix(externalReferencePrefix)).isEqualTo(1);
            assertThat(countPostingsByLedgerExternalReferencePrefix(externalReferencePrefix)).isEqualTo(2);
            assertThat(countIdempotencyRecordsByKeyPrefix(idempotencyPrefix)).isEqualTo(1);
        } finally {
            cleanupTransferRun(externalReferencePrefix, idempotencyPrefix, List.of(source, destination));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDuplicateExternalReferenceRequestsCreateOneTransferOnly() throws Exception {
        var run = shortSuffix();
        var externalReferencePrefix = "concurrent-duplicate-ref-" + run + "-";
        var idempotencyPrefix = "idem-duplicate-ref-" + run + "-";
        var source = transactionTemplate.execute(status ->
                createAccount("SRC-EXT-REF", "USD", AccountStatus.ACTIVE, 500));
        var destination = transactionTemplate.execute(status ->
                createAccount("DST-EXT-REF", "USD", AccountStatus.ACTIVE, 0));

        try {
            var runResult = runConcurrently(List.of(
                    () -> createTransferUseCase.handle(validCommand(source.getId(), destination.getId(), externalReferencePrefix + "same", idempotencyPrefix + "0")),
                    () -> createTransferUseCase.handle(validCommand(source.getId(), destination.getId(), externalReferencePrefix + "same", idempotencyPrefix + "1")),
                    () -> createTransferUseCase.handle(validCommand(source.getId(), destination.getId(), externalReferencePrefix + "same", idempotencyPrefix + "2"))
            ));

            assertThat(runResult.results()).hasSize(1);
            assertThat(runResult.failures()).hasSize(2);
            assertBalances(source.getId(), 400, 400);
            assertBalances(destination.getId(), 100, 100);
            assertThat(countTransfersByExternalReferencePrefix(externalReferencePrefix)).isEqualTo(1);
            assertThat(countLedgerTransactionsByExternalReferencePrefix(externalReferencePrefix)).isEqualTo(1);
            assertThat(countPostingsByLedgerExternalReferencePrefix(externalReferencePrefix)).isEqualTo(2);
        } finally {
            cleanupTransferRun(externalReferencePrefix, idempotencyPrefix, List.of(source, destination));
        }
    }

    private CreateTransferCommand validCommand(
            UUID sourceAccountId,
            UUID destinationAccountId,
            String externalReference,
            String idempotencyKey
    ) {
        return new CreateTransferCommand(
                sourceAccountId,
                destinationAccountId,
                CurrencyCode.of("USD"),
                100,
                externalReference,
                "test transfer",
                idempotencyKey,
                RequestedByActorType.SYSTEM,
                "corr-transfer"
        );
    }

    private AccountEntity createAccount(String prefix, String currencyCode, AccountStatus status, long balanceMinor) {
        var suffix = shortSuffix();
        var customer = customerRepository.save(CustomerEntity.builder()
                .externalCustomerReference("transfer-cust-" + suffix)
                .fullName("Transfer Test Customer")
                .email("transfer-" + suffix + "@example.com")
                .status(CustomerStatus.ACTIVE)
                .build());

        return accountRepository.save(AccountEntity.builder()
                .customer(customer)
                .accountNumber(prefix + "-" + shortSuffix())
                .accountType(AccountType.CURRENT)
                .accountCategory(AccountCategory.CUSTOMER)
                .status(status)
                .currencyCode(currencyCode)
                .availableBalanceMinor(balanceMinor)
                .ledgerBalanceMinor(balanceMinor)
                .build());
    }

    private void insertPostedLedgerTransaction(String externalReference) {
        ledgerTransactionRepository.save(dev.kavrin.banking_ledger.ledger.persistence.entity.LedgerTransactionEntity.builder()
                .externalReference(externalReference)
                .transactionType(dev.kavrin.banking_ledger.ledger.domain.model.LedgerTransactionType.TRANSFER)
                .status(dev.kavrin.banking_ledger.ledger.domain.model.TransactionStatus.POSTED)
                .currencyCode("USD")
                .amountMinor(100)
                .postedAt(java.time.OffsetDateTime.now())
                .build());
    }

    private long countTransfersByExternalReference(String externalReference) {
        return jdbcTemplate.queryForObject(
                "select count(*) from transfer_requests where external_reference = ?",
                Long.class,
                externalReference
        );
    }

    private long countTransfersByExternalReferencePrefix(String externalReferencePrefix) {
        return jdbcTemplate.queryForObject(
                "select count(*) from transfer_requests where external_reference like ?",
                Long.class,
                externalReferencePrefix + "%"
        );
    }

    private long countLedgerTransactionsByExternalReferencePrefix(String externalReferencePrefix) {
        return jdbcTemplate.queryForObject(
                "select count(*) from ledger_transactions where external_reference like ?",
                Long.class,
                externalReferencePrefix + "%"
        );
    }

    private long countPostingsByLedgerExternalReferencePrefix(String externalReferencePrefix) {
        return jdbcTemplate.queryForObject(
                """
                select count(*)
                from postings p
                join journal_entries je on je.id = p.journal_entry_id
                join ledger_transactions lt on lt.id = je.ledger_transaction_id
                where lt.external_reference like ?
                """,
                Long.class,
                externalReferencePrefix + "%"
        );
    }

    private long countIdempotencyRecordsByKeyPrefix(String idempotencyPrefix) {
        return jdbcTemplate.queryForObject(
                "select count(*) from idempotency_records where idempotency_key like ?",
                Long.class,
                idempotencyPrefix + "%"
        );
    }

    private void cleanupTransferRun(
            String externalReferencePrefix,
            String idempotencyPrefix,
            UUID sourceAccountId,
            UUID destinationAccountId,
            UUID sourceCustomerId,
            UUID destinationCustomerId
    ) {
        jdbcTemplate.update("delete from idempotency_records where idempotency_key like ?", idempotencyPrefix + "%");
        jdbcTemplate.update("delete from transfer_requests where external_reference like ?", externalReferencePrefix + "%");
        jdbcTemplate.update(
                """
                delete from outbox_events
                where aggregate_id in (
                    select id from ledger_transactions where external_reference like ?
                )
                """,
                externalReferencePrefix + "%"
        );
        jdbcTemplate.update(
                """
                delete from audit_events
                where entity_id in (
                    select id from ledger_transactions where external_reference like ?
                )
                """,
                externalReferencePrefix + "%"
        );
        jdbcTemplate.update(
                """
                delete from postings
                where journal_entry_id in (
                    select je.id
                    from journal_entries je
                    join ledger_transactions lt on lt.id = je.ledger_transaction_id
                    where lt.external_reference like ?
                )
                """,
                externalReferencePrefix + "%"
        );
        jdbcTemplate.update(
                """
                delete from journal_entries
                where ledger_transaction_id in (
                    select id from ledger_transactions where external_reference like ?
                )
                """,
                externalReferencePrefix + "%"
        );
        jdbcTemplate.update("delete from ledger_transactions where external_reference like ?", externalReferencePrefix + "%");
        jdbcTemplate.update("delete from accounts where id in (hextoraw(?), hextoraw(?))", raw(sourceAccountId), raw(destinationAccountId));
        jdbcTemplate.update("delete from customers where id in (hextoraw(?), hextoraw(?))", raw(sourceCustomerId), raw(destinationCustomerId));
    }

    private void cleanupTransferRun(
            String externalReferencePrefix,
            String idempotencyPrefix,
            List<AccountEntity> accounts
    ) {
        jdbcTemplate.update("delete from idempotency_records where idempotency_key like ?", idempotencyPrefix + "%");
        jdbcTemplate.update("delete from transfer_requests where external_reference like ?", externalReferencePrefix + "%");
        jdbcTemplate.update(
                """
                delete from outbox_events
                where aggregate_id in (
                    select id from ledger_transactions where external_reference like ?
                )
                """,
                externalReferencePrefix + "%"
        );
        jdbcTemplate.update(
                """
                delete from audit_events
                where entity_id in (
                    select id from ledger_transactions where external_reference like ?
                )
                """,
                externalReferencePrefix + "%"
        );
        jdbcTemplate.update(
                """
                delete from postings
                where journal_entry_id in (
                    select je.id
                    from journal_entries je
                    join ledger_transactions lt on lt.id = je.ledger_transaction_id
                    where lt.external_reference like ?
                )
                """,
                externalReferencePrefix + "%"
        );
        jdbcTemplate.update(
                """
                delete from journal_entries
                where ledger_transaction_id in (
                    select id from ledger_transactions where external_reference like ?
                )
                """,
                externalReferencePrefix + "%"
        );
        jdbcTemplate.update("delete from ledger_transactions where external_reference like ?", externalReferencePrefix + "%");
        for (var account : accounts) {
            jdbcTemplate.update("delete from accounts where id = hextoraw(?)", raw(account.getId()));
            jdbcTemplate.update("delete from customers where id = hextoraw(?)", raw(account.getCustomer().getId()));
        }
    }

    private ConcurrentRunResult runConcurrently(List<Callable<CreateTransferResult>> calls) throws Exception {
        var executor = Executors.newFixedThreadPool(calls.size());
        var start = new CountDownLatch(1);
        var results = Collections.synchronizedList(new ArrayList<CreateTransferResult>());
        var failures = Collections.synchronizedList(new ArrayList<Throwable>());
        try {
            var futures = calls.stream()
                    .map(call -> executor.submit(() -> {
                        await(start);
                        try {
                            results.add(call.call());
                        } catch (Throwable exception) {
                            failures.add(exception);
                        }
                        return null;
                    }))
                    .toList();

            start.countDown();
            for (var future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }

            return new ConcurrentRunResult(List.copyOf(results), List.copyOf(failures));
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertBalances(UUID accountId, long expectedAvailable, long expectedLedger) {
        var account = accountRepository.findById(accountId).orElseThrow();
        assertThat(account.getAvailableBalanceMinor()).isEqualTo(expectedAvailable);
        assertThat(account.getLedgerBalanceMinor()).isEqualTo(expectedLedger);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for latch.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for latch.", exception);
        }
    }

    private static String shortSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String raw(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    private record ConcurrentRunResult(
            List<CreateTransferResult> results,
            List<Throwable> failures
    ) {
    }
}
