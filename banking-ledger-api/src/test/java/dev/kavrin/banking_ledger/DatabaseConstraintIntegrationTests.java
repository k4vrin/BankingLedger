package dev.kavrin.banking_ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class DatabaseConstraintIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rejectsNegativePostingAmounts() {
        TestIds ids = insertPostedJournal("USD", "USD");

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into postings (
                    id, journal_entry_id, account_id, direction, currency_code, amount_minor, posted_at, created_at
                ) values (
                    hextoraw(?), hextoraw(?), hextoraw(?), 'DEBIT', 'USD', -1, systimestamp, systimestamp
                )
                """,
                raw(UUID.randomUUID()),
                raw(ids.journalEntryId()),
                raw(ids.sourceAccountId())
        ))
                .isInstanceOf(DataIntegrityViolationException.class);

        TestTransaction.flagForRollback();
    }

    @Test
    void rejectsPostingCurrencyMismatchWithAccountCurrency() {
        TestIds ids = insertPostedJournal("USD", "EUR");

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into postings (
                    id, journal_entry_id, account_id, direction, currency_code, amount_minor, posted_at, created_at
                ) values (
                    hextoraw(?), hextoraw(?), hextoraw(?), 'DEBIT', 'USD', 100, systimestamp, systimestamp
                )
                """,
                raw(UUID.randomUUID()),
                raw(ids.journalEntryId()),
                raw(ids.sourceAccountId())
        ))
                .isInstanceOf(DataIntegrityViolationException.class);

        TestTransaction.flagForRollback();
    }

    @Test
    void rejectsFailedLedgerTransactionWithoutFailureReason() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into ledger_transactions (
                    id, external_reference, transaction_type, status, currency_code, amount_minor,
                    posted_at, created_at, updated_at, version
                ) values (
                    hextoraw(?), ?, 'TRANSFER', 'FAILED', 'USD', 100,
                    null, systimestamp, systimestamp, 0
                )
                """,
                raw(UUID.randomUUID()),
                "failed-without-reason-" + UUID.randomUUID()
        ))
                .isInstanceOf(DataIntegrityViolationException.class);

        TestTransaction.flagForRollback();
    }

    private TestIds insertPostedJournal(String journalCurrency, String sourceAccountCurrency) {
        UUID customerId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();
        UUID ledgerTransactionId = UUID.randomUUID();
        UUID journalEntryId = UUID.randomUUID();
        String suffix = raw(UUID.randomUUID()).substring(0, 24);

        jdbcTemplate.update(
                """
                insert into customers (
                    id, external_customer_reference, full_name, email, status, created_at, updated_at, version
                ) values (
                    hextoraw(?), ?, 'Constraint Test Customer', ?, 'ACTIVE', systimestamp, systimestamp, 0
                )
                """,
                raw(customerId),
                "cust-" + suffix,
                "constraint-" + suffix + "@example.com"
        );

        insertAccount(sourceAccountId, customerId, "SRC" + suffix, sourceAccountCurrency);
        insertAccount(destinationAccountId, customerId, "DST" + suffix, journalCurrency);

        jdbcTemplate.update(
                """
                insert into ledger_transactions (
                    id, external_reference, transaction_type, status, currency_code, amount_minor,
                    posted_at, created_at, updated_at, version
                ) values (
                    hextoraw(?), ?, 'TRANSFER', 'POSTED', ?, 100,
                    systimestamp, systimestamp, systimestamp, 0
                )
                """,
                raw(ledgerTransactionId),
                "ledger-" + suffix,
                journalCurrency
        );

        jdbcTemplate.update(
                """
                insert into journal_entries (
                    id, ledger_transaction_id, entry_type, currency_code, total_debit_minor,
                    total_credit_minor, description, posted_at, created_at, version
                ) values (
                    hextoraw(?), hextoraw(?), 'TRANSFER', ?, 100,
                    100, 'constraint test journal', systimestamp, systimestamp, 0
                )
                """,
                raw(journalEntryId),
                raw(ledgerTransactionId),
                journalCurrency
        );

        return new TestIds(sourceAccountId, journalEntryId);
    }

    private void insertAccount(UUID accountId, UUID customerId, String accountNumber, String currencyCode) {
        jdbcTemplate.update(
                """
                insert into accounts (
                    id, customer_id, account_number, account_type, account_category, status, currency_code,
                    available_balance_minor, ledger_balance_minor, created_at, updated_at, version
                ) values (
                    hextoraw(?), hextoraw(?), ?, 'CURRENT', 'CUSTOMER', 'ACTIVE', ?,
                    1000, 1000, systimestamp, systimestamp, 0
                )
                """,
                raw(accountId),
                raw(customerId),
                accountNumber,
                currencyCode
        );
    }

    private static String raw(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    private record TestIds(UUID sourceAccountId, UUID journalEntryId) {
    }
}
