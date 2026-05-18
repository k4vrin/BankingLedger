package dev.kavrin.banking_ledger.account.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AccountPersistenceConstraintTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void accountNumberUniquenessIsEnforced() {
        UUID customerId = insertCustomer();
        String accountNumber = "UNQ" + shortSuffix();

        insertAccount(UUID.randomUUID(), customerId, accountNumber, "USD", 0, 0);

        assertThatThrownBy(() -> insertAccount(UUID.randomUUID(), customerId, accountNumber, "USD", 0, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void accountCurrencyCheckRejectsInvalidCurrencyValues() {
        UUID customerId = insertCustomer();

        assertThatThrownBy(() -> insertAccount(UUID.randomUUID(), customerId, "BADCUR" + shortSuffix(), "US1", 0, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void accountBalanceChecksRejectNegativeCachedBalances() {
        UUID customerId = insertCustomer();

        assertThatThrownBy(() -> insertAccount(UUID.randomUUID(), customerId, "NEGBAL" + shortSuffix(), "USD", -1, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertCustomer() {
        UUID customerId = UUID.randomUUID();
        String suffix = shortSuffix();

        jdbcTemplate.update(
                """
                insert into customers (
                    id, external_customer_reference, full_name, email, status, created_at, updated_at, version
                ) values (
                    hextoraw(?), ?, 'Account Constraint Customer', ?, 'ACTIVE', systimestamp, systimestamp, 0
                )
                """,
                raw(customerId),
                "constraint-customer-" + suffix,
                "account-constraint-" + suffix + "@example.com"
        );

        return customerId;
    }

    private void insertAccount(
            UUID accountId,
            UUID customerId,
            String accountNumber,
            String currencyCode,
            long availableBalanceMinor,
            long ledgerBalanceMinor
    ) {
        jdbcTemplate.update(
                """
                insert into accounts (
                    id, customer_id, account_number, account_type, account_category, status, currency_code,
                    available_balance_minor, ledger_balance_minor, created_at, updated_at, version
                ) values (
                    hextoraw(?), hextoraw(?), ?, 'CURRENT', 'CUSTOMER', 'ACTIVE', ?,
                    ?, ?, systimestamp, systimestamp, 0
                )
                """,
                raw(accountId),
                raw(customerId),
                accountNumber,
                currencyCode,
                availableBalanceMinor,
                ledgerBalanceMinor
        );
    }

    private static String raw(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    private static String shortSuffix() {
        return raw(UUID.randomUUID()).substring(0, 12);
    }
}
