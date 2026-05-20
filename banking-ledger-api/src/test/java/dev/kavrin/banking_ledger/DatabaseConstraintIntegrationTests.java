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

    @Test
    void rejectsPostedLedgerTransactionWithoutPostedTimestamp() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into ledger_transactions (
                    id, external_reference, transaction_type, status, currency_code, amount_minor,
                    posted_at, created_at, updated_at, version
                ) values (
                    hextoraw(?), ?, 'TRANSFER', 'POSTED', 'USD', 100,
                    null, systimestamp, systimestamp, 0
                )
                """,
                raw(UUID.randomUUID()),
                "posted-without-time-" + UUID.randomUUID()
        ))
                .isInstanceOf(DataIntegrityViolationException.class);

        TestTransaction.flagForRollback();
    }

    @Test
    void rejectsUnbalancedJournalTotals() {
        UUID ledgerTransactionId = insertPostedLedgerTransaction("USD");

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into journal_entries (
                    id, ledger_transaction_id, entry_type, currency_code, total_debit_minor,
                    total_credit_minor, description, posted_at, created_at, version
                ) values (
                    hextoraw(?), hextoraw(?), 'TRANSFER', 'USD', 100,
                    90, 'unbalanced journal', systimestamp, systimestamp, 0
                )
                """,
                raw(UUID.randomUUID()),
                raw(ledgerTransactionId)
        ))
                .isInstanceOf(DataIntegrityViolationException.class);

        TestTransaction.flagForRollback();
    }

    @Test
    void rejectsJournalCurrencyMismatchWithLedgerTransactionCurrency() {
        UUID ledgerTransactionId = insertPostedLedgerTransaction("USD");

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into journal_entries (
                    id, ledger_transaction_id, entry_type, currency_code, total_debit_minor,
                    total_credit_minor, description, posted_at, created_at, version
                ) values (
                    hextoraw(?), hextoraw(?), 'TRANSFER', 'EUR', 100,
                    100, 'mismatched journal currency', systimestamp, systimestamp, 0
                )
                """,
                raw(UUID.randomUUID()),
                raw(ledgerTransactionId)
        ))
                .isInstanceOf(DataIntegrityViolationException.class);

        TestTransaction.flagForRollback();
    }

    @Test
    void rejectsInvalidOutboxStatus() {
        assertThatThrownBy(() -> insertOutboxEvent("NOT_A_STATUS", 0, "{\"ok\":true}", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        TestTransaction.flagForRollback();
    }

    @Test
    void rejectsNegativeOutboxRetryCount() {
        assertThatThrownBy(() -> insertOutboxEvent("PENDING", -1, "{\"ok\":true}", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        TestTransaction.flagForRollback();
    }

    @Test
    void rejectsOutboxEventWithoutPayload() {
        assertThatThrownBy(() -> insertOutboxEvent("PENDING", 0, null, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        TestTransaction.flagForRollback();
    }

    @Test
    void rejectsOutboxEventWithoutAggregateId() {
        assertThatThrownBy(() -> insertOutboxEvent("PENDING", 0, "{\"ok\":true}", null))
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

    private UUID insertPostedLedgerTransaction(String currencyCode) {
        UUID ledgerTransactionId = UUID.randomUUID();

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
                "ledger-" + raw(UUID.randomUUID()).substring(0, 24),
                currencyCode
        );

        return ledgerTransactionId;
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

    private void insertOutboxEvent(String status, int retryCount, String payload, UUID aggregateId) {
        jdbcTemplate.update(
                """
                insert into outbox_events (
                    id, aggregate_type, aggregate_id, event_type, destination, correlation_id, event_payload,
                    status, retry_count, next_retry_at, created_at, version
                ) values (
                    hextoraw(?), 'LEDGER_TRANSACTION', hextoraw(?), 'LedgerTransactionPosted',
                    'banking-ledger.ledger-events', 'corr-constraint', ?, ?, ?, null, systimestamp, 0
                )
                """,
                raw(UUID.randomUUID()),
                aggregateId == null ? null : raw(aggregateId),
                payload,
                status,
                retryCount
        );
    }

    private static String raw(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    private record TestIds(UUID sourceAccountId, UUID journalEntryId) {
    }
}
