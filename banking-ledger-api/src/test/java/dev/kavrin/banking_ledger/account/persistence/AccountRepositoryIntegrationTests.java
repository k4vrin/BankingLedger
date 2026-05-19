package dev.kavrin.banking_ledger.account.persistence;

import dev.kavrin.banking_ledger.account.domain.model.AccountCategory;
import dev.kavrin.banking_ledger.account.domain.model.AccountStatus;
import dev.kavrin.banking_ledger.account.domain.model.AccountType;
import dev.kavrin.banking_ledger.customer.domain.model.CustomerStatus;
import dev.kavrin.banking_ledger.customer.persistence.CustomerEntity;
import dev.kavrin.banking_ledger.customer.persistence.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AccountRepositoryIntegrationTests {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findByIdForUpdateLoadsExistingAccountInsideTransaction() {
        var customer = customerRepository.save(CustomerEntity.builder()
                .externalCustomerReference("lock-customer-" + shortSuffix())
                .fullName("Locked Account Repository Customer")
                .email("lock-repository-" + shortSuffix() + "@example.com")
                .status(CustomerStatus.ACTIVE)
                .build());

        var account = accountRepository.save(AccountEntity.builder()
                .customer(customer)
                .accountNumber("LOCK-" + shortSuffix())
                .accountType(AccountType.CURRENT)
                .accountCategory(AccountCategory.CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .currencyCode("USD")
                .availableBalanceMinor(10_000)
                .ledgerBalanceMinor(10_000)
                .build());

        accountRepository.flush();

        var lockedAccount = accountRepository.findByIdForUpdate(account.getId());

        assertThat(lockedAccount).isPresent();
        assertThat(lockedAccount.orElseThrow().getId()).isEqualTo(account.getId());
        assertThat(lockedAccount.orElseThrow().getAccountNumber()).isEqualTo(account.getAccountNumber());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void findByIdForUpdateTimesOutWhenAnotherTransactionHoldsTheRowLock() throws Exception {
        var customer = customerRepository.save(CustomerEntity.builder()
                .externalCustomerReference("lock-holder-customer-" + shortSuffix())
                .fullName("Locked Account Contention Customer")
                .email("lock-contention-" + shortSuffix() + "@example.com")
                .status(CustomerStatus.ACTIVE)
                .build());

        var account = accountRepository.save(AccountEntity.builder()
                .customer(customer)
                .accountNumber("LOCK-WAIT-" + shortSuffix())
                .accountType(AccountType.CURRENT)
                .accountCategory(AccountCategory.CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .currencyCode("USD")
                .availableBalanceMinor(10_000)
                .ledgerBalanceMinor(10_000)
                .build());

        var lockHeld = new CountDownLatch(1);
        var releaseLock = new CountDownLatch(1);
        var blockedError = new AtomicReference<Throwable>();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstTransaction = executor.submit(() -> requiresNewTransaction().executeWithoutResult(status -> {
                accountRepository.findByIdForUpdate(account.getId()).orElseThrow();
                lockHeld.countDown();
                await(releaseLock);
            }));

            assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();

            var secondTransaction = executor.submit(() -> {
                try {
                    requiresNewTransaction().executeWithoutResult(status ->
                            accountRepository.findByIdForUpdate(account.getId()).orElseThrow());
                } catch (Throwable exception) {
                    blockedError.set(exception);
                }
            });

            secondTransaction.get(10, TimeUnit.SECONDS);
            assertThat(blockedError.get()).isNotNull();

            releaseLock.countDown();
            firstTransaction.get(5, TimeUnit.SECONDS);
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
            jdbcTemplate.update("delete from accounts where id = hextoraw(?)", raw(account.getId()));
            jdbcTemplate.update("delete from customers where id = hextoraw(?)", raw(customer.getId()));
        }
    }

    private TransactionTemplate requiresNewTransaction() {
        var template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
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
}
