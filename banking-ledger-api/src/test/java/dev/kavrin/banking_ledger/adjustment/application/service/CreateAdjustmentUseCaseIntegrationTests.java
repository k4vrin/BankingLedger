package dev.kavrin.banking_ledger.adjustment.application.service;

import dev.kavrin.banking_ledger.account.domain.model.AccountCategory;
import dev.kavrin.banking_ledger.account.domain.model.AccountStatus;
import dev.kavrin.banking_ledger.account.domain.model.AccountType;
import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.account.persistence.AccountRepository;
import dev.kavrin.banking_ledger.adjustment.application.command.CreateAdjustmentCommand;
import dev.kavrin.banking_ledger.adjustment.domain.model.AdjustmentReasonCode;
import dev.kavrin.banking_ledger.adjustment.domain.model.AdjustmentStatus;
import dev.kavrin.banking_ledger.adjustment.persistence.AdjustmentRequestRepository;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventRepository;
import dev.kavrin.banking_ledger.customer.domain.model.CustomerStatus;
import dev.kavrin.banking_ledger.customer.persistence.CustomerEntity;
import dev.kavrin.banking_ledger.customer.persistence.CustomerRepository;
import dev.kavrin.banking_ledger.ledger.application.command.PostingLineCommand;
import dev.kavrin.banking_ledger.ledger.domain.model.LedgerTransactionType;
import dev.kavrin.banking_ledger.ledger.domain.model.PostingDirection;
import dev.kavrin.banking_ledger.ledger.persistence.repository.LedgerTransactionRepository;
import dev.kavrin.banking_ledger.ledger.persistence.repository.PostingRepository;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxStatus;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import dev.kavrin.banking_ledger.shared.error.BusinessRuleViolationException;
import dev.kavrin.banking_ledger.shared.error.SecurityDomainException;
import dev.kavrin.banking_ledger.transfer.domain.model.RequestedByActorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CreateAdjustmentUseCaseIntegrationTests {

    private final Set<UUID> adjustmentIds = new HashSet<>();
    private final Set<UUID> ledgerTransactionIds = new HashSet<>();
    private final Set<UUID> accountIds = new HashSet<>();
    private final Set<UUID> customerIds = new HashSet<>();
    @Autowired
    private CreateAdjustmentUseCase createAdjustmentUseCase;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private AdjustmentRequestRepository adjustmentRequestRepository;
    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;
    @Autowired
    private PostingRepository postingRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String shortSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String raw(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    @AfterEach
    void cleanUp() {
        for (UUID adjustmentId : adjustmentIds) {
            jdbcTemplate.update("delete from outbox_events where aggregate_id = hextoraw(?)", raw(adjustmentId));
            jdbcTemplate.update("delete from audit_events where entity_id = hextoraw(?)", raw(adjustmentId));
            jdbcTemplate.update("delete from adjustment_requests where id = hextoraw(?)", raw(adjustmentId));
        }

        for (UUID ledgerTransactionId : ledgerTransactionIds) {
            jdbcTemplate.update("delete from outbox_events where aggregate_id = hextoraw(?)", raw(ledgerTransactionId));
            jdbcTemplate.update("delete from audit_events where entity_id = hextoraw(?)", raw(ledgerTransactionId));
            jdbcTemplate.update(
                    """
                    delete from postings
                    where journal_entry_id in (
                        select id from journal_entries where ledger_transaction_id = hextoraw(?)
                    )
                    """,
                    raw(ledgerTransactionId)
            );
            jdbcTemplate.update("delete from journal_entries where ledger_transaction_id = hextoraw(?)", raw(ledgerTransactionId));
            jdbcTemplate.update("delete from ledger_transactions where id = hextoraw(?)", raw(ledgerTransactionId));
        }

        for (UUID accountId : accountIds) {
            jdbcTemplate.update("delete from accounts where id = hextoraw(?)", raw(accountId));
        }
        for (UUID customerId : customerIds) {
            jdbcTemplate.update("delete from customers where id = hextoraw(?)", raw(customerId));
        }
    }

    @Test
    void successfulAdjustmentPersistsLedgerBalancesAuditAndOutbox() {
        var debitAccount = createAccount("ADJ-DR", "USD", AccountStatus.ACTIVE, 1_000);
        var creditAccount = createAccount("ADJ-CR", "USD", AccountStatus.ACTIVE, 250);

        var response = createAdjustmentUseCase.handle(validCommand(
                debitAccount.getId(),
                creditAccount.getId(),
                "corr-adjustment-success"
        ));

        assertThat(response.id()).isNotNull();
        assertThat(response.ledgerTransactionId()).isNotNull();
        adjustmentIds.add(response.id());
        ledgerTransactionIds.add(response.ledgerTransactionId());
        assertThat(response.status()).isEqualTo(AdjustmentStatus.COMPLETED);
        assertThat(response.reasonCode()).isEqualTo(AdjustmentReasonCode.MANUAL_CORRECTION);

        var adjustment = adjustmentRequestRepository.findById(response.id()).orElseThrow();
        assertThat(adjustment.getLedgerTransaction().getId()).isEqualTo(response.ledgerTransactionId());
        assertThat(adjustment.getCompletedAt()).isNotNull();

        var ledgerTransaction = ledgerTransactionRepository.findById(response.ledgerTransactionId()).orElseThrow();
        assertThat(ledgerTransaction.getTransactionType()).isEqualTo(LedgerTransactionType.ADJUSTMENT);
        assertThat(postingRepository.countByJournalEntry_LedgerTransaction_Id(response.ledgerTransactionId())).isEqualTo(2);

        var updatedDebitAccount = accountRepository.findById(debitAccount.getId()).orElseThrow();
        var updatedCreditAccount = accountRepository.findById(creditAccount.getId()).orElseThrow();
        assertThat(updatedDebitAccount.getAvailableBalanceMinor()).isEqualTo(900);
        assertThat(updatedDebitAccount.getLedgerBalanceMinor()).isEqualTo(900);
        assertThat(updatedCreditAccount.getAvailableBalanceMinor()).isEqualTo(350);
        assertThat(updatedCreditAccount.getLedgerBalanceMinor()).isEqualTo(350);

        assertThat(auditEventRepository.findAll())
                .filteredOn(event -> response.id().equals(event.getEntityId()))
                .anySatisfy(event -> {
                    assertThat(event.getEventType()).isEqualTo("ADJUSTMENT_POSTED");
                    assertThat(event.getEntityType()).isEqualTo("ADJUSTMENT");
                    assertThat(event.getCorrelationId()).isEqualTo("corr-adjustment-success");
                    assertThat(event.getEventPayload()).contains(response.ledgerTransactionId().toString());
                    assertThat(event.getEventPayload()).contains("MANUAL_CORRECTION");
                });

        assertThat(outboxEventRepository.findAll())
                .filteredOn(event -> response.id().equals(event.getAggregateId()))
                .anySatisfy(event -> {
                    assertThat(event.getAggregateType()).isEqualTo("ADJUSTMENT");
                    assertThat(event.getEventType()).isEqualTo("AdjustmentPosted");
                    assertThat(event.getCorrelationId()).isEqualTo("corr-adjustment-success");
                    assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
                    assertThat(event.getEventPayload()).contains(response.ledgerTransactionId().toString());
                });
    }

    @Test
    void tellerRoleIsRejectedBeforePersistence() {
        var debitAccount = createAccount("ADJ-DR", "USD", AccountStatus.ACTIVE, 1_000);
        var creditAccount = createAccount("ADJ-CR", "USD", AccountStatus.ACTIVE, 250);
        var command = new CreateAdjustmentCommand(
                "USD",
                100,
                AdjustmentReasonCode.MANUAL_CORRECTION,
                "manual correction",
                RequestedByActorType.TELLER,
                AuditActorRole.TELLER,
                "teller-1",
                "corr-adjustment-forbidden",
                postingLines(debitAccount.getId(), creditAccount.getId(), 100, "USD")
        );

        assertThatThrownBy(() -> createAdjustmentUseCase.handle(command))
                .isInstanceOfSatisfying(SecurityDomainException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Security.FORBIDDEN_RESOURCE));

        assertThat(adjustmentRequestRepository.findAll())
                .noneSatisfy(adjustment -> assertThat(adjustment.getCorrelationId()).isEqualTo("corr-adjustment-forbidden"));
        assertThat(outboxEventRepository.findAll())
                .noneSatisfy(event -> assertThat(event.getCorrelationId()).isEqualTo("corr-adjustment-forbidden"));
    }

    @Test
    void unbalancedPostingsAreRejectedBeforePersistence() {
        var debitAccount = createAccount("ADJ-DR", "USD", AccountStatus.ACTIVE, 1_000);
        var creditAccount = createAccount("ADJ-CR", "USD", AccountStatus.ACTIVE, 250);
        var command = new CreateAdjustmentCommand(
                "USD",
                100,
                AdjustmentReasonCode.MANUAL_CORRECTION,
                "manual correction",
                RequestedByActorType.OPS_ADMIN,
                AuditActorRole.OPS_ADMIN,
                "ops-1",
                "corr-adjustment-unbalanced",
                List.of(
                        new PostingLineCommand(debitAccount.getId(), PostingDirection.DEBIT, 100, "USD"),
                        new PostingLineCommand(creditAccount.getId(), PostingDirection.CREDIT, 90, "USD")
                )
        );

        assertThatThrownBy(() -> createAdjustmentUseCase.handle(command))
                .isInstanceOfSatisfying(BadRequestException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Validation.INVALID_REQUEST));

        assertThat(adjustmentRequestRepository.findAll())
                .noneSatisfy(adjustment -> assertThat(adjustment.getCorrelationId()).isEqualTo("corr-adjustment-unbalanced"));
    }

    @Test
    void postingCurrencyMismatchRollsBackAdjustmentAuditOutboxAndBalances() {
        var debitAccount = createAccount("ADJ-DR", "USD", AccountStatus.ACTIVE, 1_000);
        var creditAccount = createAccount("ADJ-CR", "EUR", AccountStatus.ACTIVE, 250);

        assertThatThrownBy(() -> createAdjustmentUseCase.handle(validCommand(
                debitAccount.getId(),
                creditAccount.getId(),
                "corr-adjustment-currency-mismatch"
        )))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.POSTING_ACCOUNT_CURRENCY_MISMATCH));

        assertNoPartialRows("corr-adjustment-currency-mismatch");
        assertThat(accountRepository.findById(debitAccount.getId()).orElseThrow().getAvailableBalanceMinor()).isEqualTo(1_000);
        assertThat(accountRepository.findById(creditAccount.getId()).orElseThrow().getAvailableBalanceMinor()).isEqualTo(250);
    }

    @Test
    void insufficientFundsRollsBackAdjustmentAuditOutboxAndBalances() {
        var debitAccount = createAccount("ADJ-DR", "USD", AccountStatus.ACTIVE, 50);
        var creditAccount = createAccount("ADJ-CR", "USD", AccountStatus.ACTIVE, 250);

        assertThatThrownBy(() -> createAdjustmentUseCase.handle(validCommand(
                debitAccount.getId(),
                creditAccount.getId(),
                "corr-adjustment-insufficient-funds"
        )))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.INSUFFICIENT_FUNDS));

        assertNoPartialRows("corr-adjustment-insufficient-funds");
        assertThat(accountRepository.findById(debitAccount.getId()).orElseThrow().getAvailableBalanceMinor()).isEqualTo(50);
        assertThat(accountRepository.findById(creditAccount.getId()).orElseThrow().getAvailableBalanceMinor()).isEqualTo(250);
    }

    @Test
    void missingPostingAccountRollsBackAdjustmentAuditAndOutbox() {
        var creditAccount = createAccount("ADJ-CR", "USD", AccountStatus.ACTIVE, 250);

        assertThatThrownBy(() -> createAdjustmentUseCase.handle(validCommand(
                UUID.randomUUID(),
                creditAccount.getId(),
                "corr-adjustment-missing-account"
        )))
                .isInstanceOfSatisfying(dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.POSTING_ACCOUNT_NOT_FOUND));

        assertNoPartialRows("corr-adjustment-missing-account");
        assertThat(accountRepository.findById(creditAccount.getId()).orElseThrow().getAvailableBalanceMinor()).isEqualTo(250);
    }

    @Test
    void frozenDebitAccountRollsBackAdjustmentAuditOutboxAndBalances() {
        var debitAccount = createAccount("ADJ-DR", "USD", AccountStatus.FROZEN, 1_000);
        var creditAccount = createAccount("ADJ-CR", "USD", AccountStatus.ACTIVE, 250);

        assertThatThrownBy(() -> createAdjustmentUseCase.handle(validCommand(
                debitAccount.getId(),
                creditAccount.getId(),
                "corr-adjustment-frozen-debit"
        )))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.INVALID_ACCOUNT_STATUS));

        assertNoPartialRows("corr-adjustment-frozen-debit");
        assertThat(accountRepository.findById(debitAccount.getId()).orElseThrow().getAvailableBalanceMinor()).isEqualTo(1_000);
        assertThat(accountRepository.findById(creditAccount.getId()).orElseThrow().getAvailableBalanceMinor()).isEqualTo(250);
    }

    @Test
    void closedCreditAccountRollsBackAdjustmentAuditOutboxAndBalances() {
        var debitAccount = createAccount("ADJ-DR", "USD", AccountStatus.ACTIVE, 1_000);
        var creditAccount = createAccount("ADJ-CR", "USD", AccountStatus.CLOSED, 250);

        assertThatThrownBy(() -> createAdjustmentUseCase.handle(validCommand(
                debitAccount.getId(),
                creditAccount.getId(),
                "corr-adjustment-closed-credit"
        )))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.INVALID_ACCOUNT_STATUS));

        assertNoPartialRows("corr-adjustment-closed-credit");
        assertThat(accountRepository.findById(debitAccount.getId()).orElseThrow().getAvailableBalanceMinor()).isEqualTo(1_000);
        assertThat(accountRepository.findById(creditAccount.getId()).orElseThrow().getAvailableBalanceMinor()).isEqualTo(250);
    }

    private void assertNoPartialRows(String correlationId) {
        assertThat(adjustmentRequestRepository.findAll())
                .noneSatisfy(adjustment -> assertThat(adjustment.getCorrelationId()).isEqualTo(correlationId));
        assertThat(auditEventRepository.findAll())
                .noneSatisfy(event -> assertThat(event.getCorrelationId()).isEqualTo(correlationId));
        assertThat(outboxEventRepository.findAll())
                .noneSatisfy(event -> assertThat(event.getCorrelationId()).isEqualTo(correlationId));
    }

    private CreateAdjustmentCommand validCommand(UUID debitAccountId, UUID creditAccountId, String correlationId) {
        return new CreateAdjustmentCommand(
                "USD",
                100,
                AdjustmentReasonCode.MANUAL_CORRECTION,
                "manual correction",
                RequestedByActorType.OPS_ADMIN,
                AuditActorRole.OPS_ADMIN,
                "ops-1",
                correlationId,
                postingLines(debitAccountId, creditAccountId, 100, "USD")
        );
    }

    private List<PostingLineCommand> postingLines(
            UUID debitAccountId,
            UUID creditAccountId,
            long amountMinor,
            String currencyCode
    ) {
        return List.of(
                new PostingLineCommand(debitAccountId, PostingDirection.DEBIT, amountMinor, currencyCode),
                new PostingLineCommand(creditAccountId, PostingDirection.CREDIT, amountMinor, currencyCode)
        );
    }

    private AccountEntity createAccount(
            String prefix,
            String currencyCode,
            AccountStatus status,
            long balanceMinor
    ) {
        var customer = customerRepository.save(CustomerEntity.builder()
                .externalCustomerReference("cust-" + shortSuffix())
                .fullName("Adjustment Test Customer")
                .email("adjustment-" + shortSuffix() + "@example.com")
                .status(CustomerStatus.ACTIVE)
                .build());
        customerIds.add(customer.getId());

        var account = accountRepository.save(AccountEntity.builder()
                .customer(customer)
                .accountNumber(prefix + "-" + shortSuffix())
                .accountType(AccountType.CURRENT)
                .accountCategory(AccountCategory.CUSTOMER)
                .status(status)
                .currencyCode(currencyCode)
                .availableBalanceMinor(balanceMinor)
                .ledgerBalanceMinor(balanceMinor)
                .build());
        accountIds.add(account.getId());
        return account;
    }
}
