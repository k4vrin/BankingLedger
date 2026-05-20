package dev.kavrin.banking_ledger.reporting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReportJdbcIntegrationTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void dailyTrialBalance_shouldReturnExpectedTotalsForKnownLedgerData() throws Exception {
        seedReportData();

        var sql = loadSql("reports/sql/daily_trial_balance.sql");

        var rows = jdbc.queryForList(
                sql,
                new MapSqlParameterSource()
                        .addValue("report_date", java.sql.Date.valueOf("2026-05-20"))
        );

        assertThat(rows).isNotEmpty();

        var usdCustomer = rows.stream()
                .filter(row -> "USD".equals(row.get("CURRENCY_CODE")))
                .filter(row -> "CUSTOMER".equals(row.get("ACCOUNT_CATEGORY")))
                .findFirst()
                .orElseThrow();

        assertThat(usdCustomer.get("REPORT_DEBIT_MINOR")).isEqualTo(number(4000));
        assertThat(usdCustomer.get("REPORT_CREDIT_MINOR")).isEqualTo(number(4000));
        assertThat(usdCustomer.get("REPORT_NET_MINOR")).isEqualTo(number(0));
    }

    @Test
    void accountStatement_shouldIncludeTransferReversalAndAdjustmentRows() throws Exception {
        seedReportData();

        var sql = loadSql("reports/sql/account_statement_summary.sql");

        var rows = jdbc.queryForList(
                sql,
                new MapSqlParameterSource()
                        .addValue("account_id", raw("11111111111111111111111111111111"))
                        .addValue("from_date", Timestamp.valueOf("2026-05-20 00:00:00"))
                        .addValue("to_date", Timestamp.valueOf("2026-05-21 00:00:00"))
        );

        assertThat(rows).hasSize(3);

        assertThat(rows)
                .extracting(row -> row.get("DESCRIPTION"))
                .contains(
                        "Initial transfer",
                        "Transfer reversal",
                        "Manual adjustment"
                );
    }

    private void seedReportData() {
        jdbc.getJdbcTemplate().execute("""
            delete from postings
            """);
        jdbc.getJdbcTemplate().execute("""
            delete from journal_entries
            """);
        jdbc.getJdbcTemplate().execute("""
            delete from ledger_transactions
            """);
        jdbc.getJdbcTemplate().execute("""
            delete from accounts
            """);
        jdbc.getJdbcTemplate().execute("""
            delete from customers
            """);

        jdbc.update("""
            insert into customers (
                id, external_customer_reference, full_name, email, status,
                created_at, updated_at, version
            ) values (
                :id, 'CUST-REPORT-1', 'Report Customer', 'report@example.com', 'ACTIVE',
                :now, :now, 0
            )
            """,
                params()
                        .addValue("id", raw("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
        );

        jdbc.update("""
            insert into accounts (
                id, customer_id, account_number, account_type, account_category,
                status, currency_code, available_balance_minor, ledger_balance_minor,
                created_at, updated_at, version
            ) values (
                :id, :customer_id, :account_number, :account_type, :account_category,
                'ACTIVE', 'USD', 0, 0, :now, :now, 0
            )
            """,
                params()
                        .addValue("id", raw("11111111111111111111111111111111"))
                        .addValue("customer_id", raw("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                        .addValue("account_number", "ACC-CUSTOMER-1")
                        .addValue("account_type", "CURRENT")
                        .addValue("account_category", "CUSTOMER")
        );

        jdbc.update("""
            insert into accounts (
                id, customer_id, account_number, account_type, account_category,
                status, currency_code, available_balance_minor, ledger_balance_minor,
                created_at, updated_at, version
            ) values (
                :id, :customer_id, :account_number, :account_type, :account_category,
                'ACTIVE', 'USD', 0, 0, :now, :now, 0
            )
            """,
                params()
                        .addValue("id", raw("22222222222222222222222222222222"))
                        .addValue("customer_id", raw("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                        .addValue("account_number", "ACC-CUSTOMER-2")
                        .addValue("account_type", "CURRENT")
                        .addValue("account_category", "CUSTOMER")
        );

        insertPostedLedger(
                raw("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
                raw("cccccccccccccccccccccccccccccccc"),
                "TRANSFER",
                "Initial transfer",
                1000,
                "2026-05-20 09:00:00",
                raw("11111111111111111111111111111111"),
                "CREDIT",
                raw("22222222222222222222222222222222"),
                "DEBIT"
        );

        insertPostedLedger(
                raw("dddddddddddddddddddddddddddddddd"),
                raw("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"),
                "REVERSAL",
                "Transfer reversal",
                1000,
                "2026-05-20 10:00:00",
                raw("11111111111111111111111111111111"),
                "DEBIT",
                raw("22222222222222222222222222222222"),
                "CREDIT"
        );

        insertPostedLedger(
                raw("ffffffffffffffffffffffffffffffff"),
                raw("99999999999999999999999999999999"),
                "ADJUSTMENT",
                "Manual adjustment",
                2000,
                "2026-05-20 11:00:00",
                raw("11111111111111111111111111111111"),
                "DEBIT",
                raw("22222222222222222222222222222222"),
                "CREDIT"
        );
    }

    private void insertPostedLedger(
            byte[] transactionId,
            byte[] journalEntryId,
            String type,
            String description,
            long amountMinor,
            String postedAt,
            byte[] firstAccountId,
            String firstDirection,
            byte[] secondAccountId,
            String secondDirection
    ) {
        var postedTimestamp = Timestamp.valueOf(postedAt);

        jdbc.update("""
            insert into ledger_transactions (
                id, transaction_type, status, currency_code, amount_minor,
                description, posted_at, created_at, updated_at, version
            ) values (
                :id, :type, 'POSTED', 'USD', :amount_minor,
                :description, :posted_at, :posted_at, :posted_at, 0
            )
            """,
                params()
                        .addValue("id", transactionId)
                        .addValue("type", type)
                        .addValue("amount_minor", amountMinor)
                        .addValue("description", description)
                        .addValue("posted_at", postedTimestamp)
        );

        jdbc.update("""
            insert into journal_entries (
                id, ledger_transaction_id, entry_type, currency_code,
                total_debit_minor, total_credit_minor, description,
                posted_at, created_at, version
            ) values (
                :id, :ledger_transaction_id, :type, 'USD',
                :amount_minor, :amount_minor, :description,
                :posted_at, :posted_at, 0
            )
            """,
                params()
                        .addValue("id", journalEntryId)
                        .addValue("ledger_transaction_id", transactionId)
                        .addValue("type", type)
                        .addValue("amount_minor", amountMinor)
                        .addValue("description", description)
                        .addValue("posted_at", postedTimestamp)
        );

        insertPosting(journalEntryId, firstAccountId, firstDirection, amountMinor, postedTimestamp);
        insertPosting(journalEntryId, secondAccountId, secondDirection, amountMinor, postedTimestamp);
    }

    private void insertPosting(
            byte[] journalEntryId,
            byte[] accountId,
            String direction,
            long amountMinor,
            Timestamp postedAt
    ) {
        jdbc.update("""
            insert into postings (
                id, journal_entry_id, account_id, direction,
                currency_code, amount_minor, posted_at, created_at
            ) values (
                sys_guid(), :journal_entry_id, :account_id, :direction,
                'USD', :amount_minor, :posted_at, :posted_at
            )
            """,
                params()
                        .addValue("journal_entry_id", journalEntryId)
                        .addValue("account_id", accountId)
                        .addValue("direction", direction)
                        .addValue("amount_minor", amountMinor)
                        .addValue("posted_at", postedAt)
        );
    }

    private MapSqlParameterSource params() {
        return new MapSqlParameterSource()
                .addValue("now", Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 8, 0)));
    }

    private String loadSql(String path) throws Exception {
        var resource = new ClassPathResource(path);
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    private byte[] raw(String hex) {
        var bytes = new byte[hex.length() / 2];

        for (int i = 0; i < bytes.length; i++) {
            var index = i * 2;
            bytes[i] = (byte) Integer.parseInt(hex.substring(index, index + 2), 16);
        }

        return bytes;
    }

    private Object number(long value) {
        return java.math.BigDecimal.valueOf(value);
    }
}