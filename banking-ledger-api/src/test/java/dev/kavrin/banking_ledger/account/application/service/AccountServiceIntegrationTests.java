package dev.kavrin.banking_ledger.account.application.service;

import dev.kavrin.banking_ledger.account.application.command.CreateAccountCommand;
import dev.kavrin.banking_ledger.account.application.query.GetAccountBalanceQuery;
import dev.kavrin.banking_ledger.account.application.query.GetAccountByIdQuery;
import dev.kavrin.banking_ledger.account.application.query.GetAccountByNumberQuery;
import dev.kavrin.banking_ledger.account.application.query.GetAccountTransactionsQuery;
import dev.kavrin.banking_ledger.account.domain.model.AccountStatus;
import dev.kavrin.banking_ledger.account.domain.model.AccountType;
import dev.kavrin.banking_ledger.account.domain.policy.AccountStatusPolicy;
import dev.kavrin.banking_ledger.account.persistence.AccountRepository;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventRepository;
import dev.kavrin.banking_ledger.customer.domain.model.CustomerStatus;
import dev.kavrin.banking_ledger.customer.persistence.CustomerEntity;
import dev.kavrin.banking_ledger.customer.persistence.CustomerRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import dev.kavrin.banking_ledger.shared.error.BusinessRuleViolationException;
import dev.kavrin.banking_ledger.shared.error.ConflictException;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AccountServiceIntegrationTests {

    @Autowired
    private CreateAccountUseCase createAccountUseCase;

    @Autowired
    private AccountQueryUseCase accountQueryUseCase;

    @Autowired
    private GetAccountBalanceUseCase getAccountBalanceUseCase;

    @Autowired
    private GetAccountTransactionsUseCase getAccountTransactionsUseCase;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Test
    void createsAccountWithZeroBalancesAndAuditEvent() {
        var customer = createCustomer();

        var response = createAccountUseCase.handle(new CreateAccountCommand(
                customer.getId(),
                "ACC-" + shortSuffix(),
                AccountType.CURRENT,
                CurrencyCode.of("usd"),
                null,
                "corr-service-create"
        ));

        assertThat(response.id()).isNotNull();
        assertThat(response.customerId()).isEqualTo(customer.getId());
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.currencyCode()).isEqualTo("USD");
        assertThat(response.availableBalanceMinor()).isZero();
        assertThat(response.ledgerBalanceMinor()).isZero();

        var savedAccount = accountRepository.findById(response.id()).orElseThrow();
        assertThat(savedAccount.getAccountNumber()).isEqualTo(response.accountNumber());

        var auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).anySatisfy(event -> {
            assertThat(event.getEntityType()).isEqualTo("ACCOUNT");
            assertThat(event.getEntityId()).isEqualTo(response.id());
            assertThat(event.getEventType()).isEqualTo("ACCOUNT_CREATED");
            assertThat(event.getActorType()).isEqualTo(AuditActorType.SYSTEM);
            assertThat(event.getCorrelationId()).isEqualTo("corr-service-create");
        });
    }

    @Test
    void rejectsDuplicateAccountNumberWithDomainConflict() {
        var customer = createCustomer();
        var accountNumber = "DUP-" + shortSuffix();

        createAccountUseCase.handle(new CreateAccountCommand(
                customer.getId(),
                accountNumber,
                AccountType.CURRENT,
                CurrencyCode.of("USD"),
                "SYSTEM",
                null
        ));

        assertThatThrownBy(() -> createAccountUseCase.handle(new CreateAccountCommand(
                customer.getId(),
                accountNumber,
                AccountType.CURRENT,
                CurrencyCode.of("USD"),
                "SYSTEM",
                null
        )))
                .isInstanceOfSatisfying(ConflictException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.ACCOUNT_NUMBER_ALREADY_EXISTS));
    }

    @Test
    void rejectsMissingCustomerWithDomainNotFound() {
        assertThatThrownBy(() -> createAccountUseCase.handle(new CreateAccountCommand(
                UUID.randomUUID(),
                "MISS-" + shortSuffix(),
                AccountType.CURRENT,
                CurrencyCode.of("USD"),
                "SYSTEM",
                null
        )))
                .isInstanceOfSatisfying(ResourceNotFoundException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.CUSTOMER_NOT_FOUND));
    }

    @Test
    void rejectsInternalOnlyAccountTypeForCustomerAccount() {
        var customer = createCustomer();

        assertThatThrownBy(() -> createAccountUseCase.handle(new CreateAccountCommand(
                customer.getId(),
                "INT-" + shortSuffix(),
                AccountType.SUSPENSE,
                CurrencyCode.of("USD"),
                "SYSTEM",
                null
        )))
                .isInstanceOfSatisfying(BadRequestException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.INVALID_ACCOUNT_TYPE));
    }

    @Test
    void rejectsInvalidActorTypeWithDomainBadRequest() {
        var customer = createCustomer();

        assertThatThrownBy(() -> createAccountUseCase.handle(new CreateAccountCommand(
                customer.getId(),
                "ACT-" + shortSuffix(),
                AccountType.CURRENT,
                CurrencyCode.of("USD"),
                "not-a-real-actor",
                null
        )))
                .isInstanceOfSatisfying(BadRequestException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Validation.INVALID_REQUEST));
    }

    @Test
    void looksUpAccountByIdAndNumber() {
        var customer = createCustomer();
        var created = createAccountUseCase.handle(new CreateAccountCommand(
                customer.getId(),
                "LOOK-" + shortSuffix(),
                AccountType.SAVINGS,
                CurrencyCode.of("USD"),
                "SYSTEM",
                null
        ));

        var byId = accountQueryUseCase.getById(new GetAccountByIdQuery(created.id()));
        var byNumber = accountQueryUseCase.getByNumber(new GetAccountByNumberQuery(created.accountNumber()));

        assertThat(byId).isEqualTo(created);
        assertThat(byNumber).isEqualTo(created);
    }

    @Test
    void missingAccountLookupUsesDomainNotFound() {
        assertThatThrownBy(() -> accountQueryUseCase.getById(new GetAccountByIdQuery(UUID.randomUUID())))
                .isInstanceOfSatisfying(ResourceNotFoundException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.ACCOUNT_NOT_FOUND));
    }

    @Test
    void balanceQueryReturnsCachedBalances() {
        var customer = createCustomer();
        var account = createAccountUseCase.handle(new CreateAccountCommand(
                customer.getId(),
                "BAL-" + shortSuffix(),
                AccountType.CURRENT,
                CurrencyCode.of("USD"),
                "SYSTEM",
                null
        ));

        var balance = getAccountBalanceUseCase.handle(new GetAccountBalanceQuery(account.id()));

        assertThat(balance.accountId()).isEqualTo(account.id());
        assertThat(balance.currencyCode()).isEqualTo("USD");
        assertThat(balance.availableBalanceMinor()).isZero();
        assertThat(balance.ledgerBalanceMinor()).isZero();
    }

    @Test
    void transactionHistoryReturnsEmptyPageForExistingAccount() {
        var customer = createCustomer();
        var account = createAccountUseCase.handle(new CreateAccountCommand(
                customer.getId(),
                "HIST-" + shortSuffix(),
                AccountType.CURRENT,
                CurrencyCode.of("USD"),
                "SYSTEM",
                null
        ));

        var history = getAccountTransactionsUseCase.handle(new GetAccountTransactionsQuery(
                account.id(),
                null,
                null,
                0,
                20
        ));

        assertThat(history.getTotalElements()).isZero();
    }

    @Test
    void transactionHistoryRejectsMissingAccountAndInvalidPageRequest() {
        assertThatThrownBy(() -> getAccountTransactionsUseCase.handle(new GetAccountTransactionsQuery(
                UUID.randomUUID(),
                null,
                null,
                0,
                20
        )))
                .isInstanceOfSatisfying(ResourceNotFoundException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.ACCOUNT_NOT_FOUND));

        assertThatThrownBy(() -> getAccountTransactionsUseCase.handle(new GetAccountTransactionsQuery(
                UUID.randomUUID(),
                null,
                null,
                -1,
                20
        )))
                .isInstanceOfSatisfying(BadRequestException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Validation.INVALID_REQUEST));
    }

    @Test
    void statusPolicyAllowsActiveDebitAndCredit() {
        assertThat(AccountStatusPolicy.canDebit(AccountStatus.ACTIVE)).isTrue();
        assertThat(AccountStatusPolicy.canCredit(AccountStatus.ACTIVE)).isTrue();
    }

    @Test
    void statusPolicyRejectsFrozenAndClosedDebitsWithDomainError() {
        assertThat(AccountStatusPolicy.canDebit(AccountStatus.FROZEN)).isFalse();
        assertThat(AccountStatusPolicy.canDebit(AccountStatus.CLOSED)).isFalse();
        assertThat(AccountStatusPolicy.canCredit(AccountStatus.FROZEN)).isTrue();

        assertThatThrownBy(() -> AccountStatusPolicy.validateCanDebit(AccountStatus.FROZEN))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.INVALID_ACCOUNT_STATUS));
    }

    private CustomerEntity createCustomer() {
        return customerRepository.save(CustomerEntity.builder()
                .externalCustomerReference("cust-" + shortSuffix())
                .fullName("Phase 2 Test Customer")
                .email("phase2-" + shortSuffix() + "@example.com")
                .status(CustomerStatus.ACTIVE)
                .build());
    }

    private static String shortSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
