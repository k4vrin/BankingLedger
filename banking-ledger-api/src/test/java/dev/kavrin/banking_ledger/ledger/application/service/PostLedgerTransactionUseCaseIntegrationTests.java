package dev.kavrin.banking_ledger.ledger.application.service;

import dev.kavrin.banking_ledger.account.domain.model.AccountCategory;
import dev.kavrin.banking_ledger.account.domain.model.AccountStatus;
import dev.kavrin.banking_ledger.account.domain.model.AccountType;
import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.account.persistence.AccountRepository;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventRepository;
import dev.kavrin.banking_ledger.customer.domain.model.CustomerStatus;
import dev.kavrin.banking_ledger.customer.persistence.CustomerEntity;
import dev.kavrin.banking_ledger.customer.persistence.CustomerRepository;
import dev.kavrin.banking_ledger.ledger.application.command.PostLedgerTransactionCommand;
import dev.kavrin.banking_ledger.ledger.application.command.PostingLineCommand;
import dev.kavrin.banking_ledger.ledger.domain.model.PostingDirection;
import dev.kavrin.banking_ledger.ledger.persistence.repository.JournalEntryRepository;
import dev.kavrin.banking_ledger.ledger.persistence.repository.LedgerTransactionRepository;
import dev.kavrin.banking_ledger.ledger.persistence.repository.PostingRepository;
import dev.kavrin.banking_ledger.outbox.OutboxStatus;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import dev.kavrin.banking_ledger.shared.error.BusinessRuleViolationException;
import dev.kavrin.banking_ledger.shared.error.ConflictException;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PostLedgerTransactionUseCaseIntegrationTests {

    @Autowired
    private PostLedgerTransactionUseCase postLedgerTransactionUseCase;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private PostingRepository postingRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Set<UUID> ledgerTransactionIds = new HashSet<>();
    private final Set<UUID> accountIds = new HashSet<>();
    private final Set<UUID> customerIds = new HashSet<>();
    private final String runId = shortSuffix();

    @AfterEach
    void cleanUp() {
        for (UUID transactionId : ledgerTransactionIds) {
            jdbcTemplate.update(
                    "delete from outbox_events where aggregate_id = hextoraw(?)",
                    raw(transactionId)
            );
            jdbcTemplate.update(
                    "delete from audit_events where entity_id = hextoraw(?)",
                    raw(transactionId)
            );
            jdbcTemplate.update(
                    """
                    delete from postings
                    where journal_entry_id in (
                        select id from journal_entries where ledger_transaction_id = hextoraw(?)
                    )
                    """,
                    raw(transactionId)
            );
            jdbcTemplate.update(
                    "delete from journal_entries where ledger_transaction_id = hextoraw(?)",
                    raw(transactionId)
            );
            jdbcTemplate.update(
                    "delete from ledger_transactions where id = hextoraw(?)",
                    raw(transactionId)
            );
        }

        for (UUID accountId : accountIds) {
            jdbcTemplate.update("delete from accounts where id = hextoraw(?)", raw(accountId));
        }
        for (UUID customerId : customerIds) {
            jdbcTemplate.update("delete from customers where id = hextoraw(?)", raw(customerId));
        }
    }

    @Test
    void validCommandPersistsLedgerGraphAuditOutboxAndBalances() {
        var source = createAccount("SRC", "USD", 1_000);
        var destination = createAccount("DST", "USD", 1_000);

        var result = postLedgerTransactionUseCase.handle(validCommand(source.getId(), destination.getId(), ref("ledger-ok")));
        ledgerTransactionIds.add(result.ledgerTransactionId());

        assertThat(ledgerTransactionRepository.existsById(result.ledgerTransactionId())).isTrue();
        assertThat(journalEntryRepository.countByLedgerTransaction_Id(result.ledgerTransactionId())).isEqualTo(1);
        assertThat(postingRepository.countByJournalEntry_LedgerTransaction_Id(result.ledgerTransactionId())).isEqualTo(2);
        assertThat(result.ledgerTransactionId()).isNotNull();
        assertThat(result.journalEntryId()).isNotNull();
        assertThat(result.postingIds()).hasSize(2);

        var updatedSource = accountRepository.findById(source.getId()).orElseThrow();
        var updatedDestination = accountRepository.findById(destination.getId()).orElseThrow();
        assertThat(updatedSource.getAvailableBalanceMinor()).isEqualTo(900);
        assertThat(updatedSource.getLedgerBalanceMinor()).isEqualTo(900);
        assertThat(updatedDestination.getAvailableBalanceMinor()).isEqualTo(1_100);
        assertThat(updatedDestination.getLedgerBalanceMinor()).isEqualTo(1_100);

        assertThat(auditEventRepository.findAll()).filteredOn(event -> result.ledgerTransactionId().equals(event.getEntityId())).anySatisfy(event -> {
            assertThat(event.getEventType()).isEqualTo("LEDGER_TRANSACTION_POSTED");
            assertThat(event.getEntityType()).isEqualTo("LEDGER_TRANSACTION");
            assertThat(event.getEntityId()).isEqualTo(result.ledgerTransactionId());
            assertThat(event.getCorrelationId()).isEqualTo("corr-ledger");
        });

        assertThat(outboxEventRepository.findAll()).filteredOn(event -> result.ledgerTransactionId().equals(event.getAggregateId())).anySatisfy(event -> {
            assertThat(event.getAggregateType()).isEqualTo("LEDGER_TRANSACTION");
            assertThat(event.getAggregateId()).isEqualTo(result.ledgerTransactionId());
            assertThat(event.getEventType()).isEqualTo("LedgerTransactionPosted");
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getEventPayload()).contains("\"amountMinor\":100");
        });
    }

    @Test
    void duplicateExternalReferenceIsRejected() {
        var source = createAccount("SRC", "USD", 1_000);
        var destination = createAccount("DST", "USD", 1_000);
        var externalReference = ref("duplicate-ledger");
        var result = postLedgerTransactionUseCase.handle(validCommand(source.getId(), destination.getId(), externalReference));
        ledgerTransactionIds.add(result.ledgerTransactionId());

        assertThatThrownBy(() -> postLedgerTransactionUseCase.handle(validCommand(
                source.getId(),
                destination.getId(),
                externalReference
        )))
                .isInstanceOfSatisfying(ConflictException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.LEDGER_TRANSACTION_ALREADY_EXISTS));
    }

    @Test
    void missingPostingAccountIsRejectedBeforePersistence() {
        var source = createAccount("SRC", "USD", 1_000);

        assertThatThrownBy(() -> postLedgerTransactionUseCase.handle(validCommand(
                source.getId(),
                UUID.randomUUID(),
                ref("missing-account")
        )))
                .isInstanceOfSatisfying(ResourceNotFoundException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.POSTING_ACCOUNT_NOT_FOUND));

        assertThat(ledgerTransactionRepository.existsByExternalReference(ref("missing-account"))).isFalse();
    }

    @Test
    void accountCurrencyMismatchIsRejectedBeforePersistence() {
        var source = createAccount("SRC", "USD", 1_000);
        var destination = createAccount("DST", "EUR", 1_000);

        assertThatThrownBy(() -> postLedgerTransactionUseCase.handle(validCommand(
                source.getId(),
                destination.getId(),
                ref("currency-mismatch")
        )))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.POSTING_ACCOUNT_CURRENCY_MISMATCH));

        assertThat(ledgerTransactionRepository.existsByExternalReference(ref("currency-mismatch"))).isFalse();
    }

    @Test
    void failureAfterPostingSaveRollsBackLedgerJournalPostingsAuditAndOutbox() {
        var source = createAccount("SRC", "USD", 1_000);
        var destination = createAccount("DST", "USD", 1_000);
        var command = new PostLedgerTransactionCommand(
                ref("rollback-after-postings"),
                "TRANSFER",
                "USD",
                100,
                "rollback test",
                "not-a-real-actor",
                "corr-rollback",
                java.util.List.of(
                        new PostingLineCommand(source.getId(), PostingDirection.DEBIT, 100, "USD"),
                        new PostingLineCommand(destination.getId(), PostingDirection.CREDIT, 100, "USD")
                )
        );

        assertThatThrownBy(() -> postLedgerTransactionUseCase.handle(command))
                .isInstanceOfSatisfying(BadRequestException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Validation.INVALID_REQUEST));

        assertThat(ledgerTransactionRepository.existsByExternalReference(ref("rollback-after-postings"))).isFalse();
        assertThat(outboxEventRepository.findAll())
                .noneSatisfy(event -> assertThat(event.getCorrelationId()).isEqualTo("corr-rollback"));
    }

    private PostLedgerTransactionCommand validCommand(UUID sourceAccountId, UUID destinationAccountId, String externalReference) {
        return new PostLedgerTransactionCommand(
                externalReference,
                "TRANSFER",
                "USD",
                100,
                "test transfer",
                "SYSTEM",
                "corr-ledger",
                java.util.List.of(
                        new PostingLineCommand(sourceAccountId, PostingDirection.DEBIT, 100, "USD"),
                        new PostingLineCommand(destinationAccountId, PostingDirection.CREDIT, 100, "USD")
                )
        );
    }

    private AccountEntity createAccount(String prefix, String currencyCode, long balanceMinor) {
        var customer = customerRepository.save(CustomerEntity.builder()
                .externalCustomerReference("cust-" + shortSuffix())
                .fullName("Ledger Test Customer")
                .email("ledger-" + shortSuffix() + "@example.com")
                .status(CustomerStatus.ACTIVE)
                .build());
        customerIds.add(customer.getId());

        var account = accountRepository.save(AccountEntity.builder()
                .customer(customer)
                .accountNumber(prefix + "-" + shortSuffix())
                .accountType(AccountType.CURRENT)
                .accountCategory(AccountCategory.CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .currencyCode(currencyCode)
                .availableBalanceMinor(balanceMinor)
                .ledgerBalanceMinor(balanceMinor)
                .build());
        accountIds.add(account.getId());
        return account;
    }

    private static String shortSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String ref(String value) {
        return value + "-" + runId;
    }

    private static String raw(UUID uuid) {
        return uuid.toString().replace("-", "");
    }
}
