package dev.kavrin.banking_ledger.reporting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void reconciliationMismatchReport_shouldFilterAndSortBatchMismatches() throws Exception {
        seedReportData();
        seedReconciliationData();

        var sql = loadSql("reports/sql/reconciliation_mismatch_report.sql");

        var rows = jdbc.queryForList(
                sql,
                new MapSqlParameterSource()
                        .addValue("batch_id", raw("abababababababababababababababab"))
                        .addValue("mismatch_type", "AMOUNT_MISMATCH")
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("MISMATCH_TYPE")).isEqualTo("AMOUNT_MISMATCH");
        assertThat(rows.getFirst().get("SEVERITY")).isEqualTo("CRITICAL");
        assertThat(rows.getFirst().get("DETAIL")).isEqualTo("External amount differs from ledger amount.");
    }

    @Test
    void suspenseAging_shouldBucketInternalSuspensePostingsByCurrency() throws Exception {
        seedReportData();
        seedSuspenseData();

        var sql = loadSql("reports/sql/suspense_account_aging_report.sql");

        var rows = jdbc.queryForList(
                sql,
                new MapSqlParameterSource()
                        .addValue("as_of_date", java.sql.Date.valueOf("2026-05-20"))
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("CURRENCY_CODE")).isEqualTo("USD");
        assertThat(rows.getFirst().get("AGE_BUCKET")).isEqualTo("8-30 days");
        assertThat(rows.getFirst().get("TOTAL_DEBIT_MINOR")).isEqualTo(number(300));
        assertThat(rows.getFirst().get("NET_AMOUNT_MINOR")).isEqualTo(number(300));
    }

    @Test
    void topFailedTransferReasons_shouldGroupFailuresByReasonAndCurrency() throws Exception {
        seedReportData();
        seedFailedTransfers();

        var sql = loadSql("reports/sql/top_failed_transfer_reasons.sql");

        var rows = jdbc.queryForList(
                sql,
                new MapSqlParameterSource()
                        .addValue("from_date", Timestamp.valueOf("2026-05-20 00:00:00"))
                        .addValue("to_date", Timestamp.valueOf("2026-05-21 00:00:00"))
        );

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().get("FAILURE_REASON_CODE")).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(rows.getFirst().get("FAILURE_COUNT")).isEqualTo(number(2));
        assertThat(rows.getFirst().get("TOTAL_FAILED_AMOUNT_MINOR")).isEqualTo(number(1500));
    }

    @Test
    void ledgerActivityCteReport_shouldReturnOracleCteAndAnalyticTotals() throws Exception {
        seedReportData();

        var sql = loadSql("reports/sql/ledger_activity_cte_report.sql");

        var rows = jdbc.queryForList(
                sql,
                new MapSqlParameterSource()
                        .addValue("from_date", Timestamp.valueOf("2026-05-20 00:00:00"))
                        .addValue("to_date", Timestamp.valueOf("2026-05-21 00:00:00"))
        );

        assertThat(sql.toLowerCase()).contains("with filtered_postings as", "over (");
        assertThat(rows).isNotEmpty();
        assertThat(rows.getFirst()).containsKeys(
                "CURRENCY_CODE",
                "ACCOUNT_CATEGORY",
                "AGE_BUCKET",
                "CATEGORY_RUNNING_NET_MINOR"
        );
    }

    private void seedReportData() {
        jdbc.getJdbcTemplate().execute("""
            delete from reconciliation_results
            """);
        jdbc.getJdbcTemplate().execute("""
            delete from settlement_items
            """);
        jdbc.getJdbcTemplate().execute("""
            delete from settlement_batches
            """);
        jdbc.getJdbcTemplate().execute("""
            delete from transfer_requests
            """);
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

    private void seedReconciliationData() {
        jdbc.update("""
            insert into settlement_batches (
                id, source, reference_name, imported_by_actor, correlation_id, status,
                imported_at, completed_at, item_count, matched_count, mismatch_count, version
            ) values (
                :id, 'processor-a', 'processor-a-2026-05-20.csv', 'ops-user-1', 'corr-report', 'COMPLETED',
                :now, :now, 2, 1, 1, 0
            )
            """,
                params().addValue("id", raw("abababababababababababababababab"))
        );

        jdbc.update("""
            insert into settlement_items (
                id, batch_id, source, external_transaction_reference, amount_minor, currency_code,
                status, settlement_date, raw_line_hash, metadata_json, created_at, version
            ) values (
                :id, :batch_id, 'processor-a', 'EXT-REPORT-1', 1200, 'USD',
                'SETTLED', date '2026-05-20', 'hash-report-1', '{"processor":"processor-a"}', :now, 0
            )
            """,
                params()
                        .addValue("id", raw("bcbcbcbcbcbcbcbcbcbcbcbcbcbcbcbc"))
                        .addValue("batch_id", raw("abababababababababababababababab"))
        );

        jdbc.update("""
            insert into reconciliation_results (
                id, batch_id, item_id, ledger_transaction_id, mismatch_type, severity,
                status, detail, created_at, resolved_at, version
            ) values (
                :id, :batch_id, :item_id, :ledger_transaction_id, 'AMOUNT_MISMATCH', 'CRITICAL',
                'OPEN', 'External amount differs from ledger amount.', :now, null, 0
            )
            """,
                params()
                        .addValue("id", raw("cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd"))
                        .addValue("batch_id", raw("abababababababababababababababab"))
                        .addValue("item_id", raw("bcbcbcbcbcbcbcbcbcbcbcbcbcbcbcbc"))
                        .addValue("ledger_transaction_id", raw("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
        );

        jdbc.update("""
            insert into reconciliation_results (
                id, batch_id, item_id, ledger_transaction_id, mismatch_type, severity,
                status, detail, created_at, resolved_at, version
            ) values (
                :id, :batch_id, :item_id, :ledger_transaction_id, 'MATCHED', 'INFO',
                'RESOLVED', 'Exact match.', :now, :now, 0
            )
            """,
                params()
                        .addValue("id", raw("dededededededededededededededede"))
                        .addValue("batch_id", raw("abababababababababababababababab"))
                        .addValue("item_id", raw("bcbcbcbcbcbcbcbcbcbcbcbcbcbcbcbc"))
                        .addValue("ledger_transaction_id", raw("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
        );
    }

    private void seedSuspenseData() {
        jdbc.update("""
            insert into accounts (
                id, customer_id, account_number, account_type, account_category,
                status, currency_code, available_balance_minor, ledger_balance_minor,
                created_at, updated_at, version
            ) values (
                :id, :customer_id, 'ACC-SUSPENSE-1', 'SUSPENSE', 'INTERNAL',
                'ACTIVE', 'USD', 0, 0, :now, :now, 0
            )
            """,
                params()
                        .addValue("id", raw("33333333333333333333333333333333"))
                        .addValue("customer_id", raw("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
        );

        insertPostedLedger(
                raw("34343434343434343434343434343434"),
                raw("45454545454545454545454545454545"),
                "ADJUSTMENT",
                "Suspense investigation item",
                300,
                "2026-05-10 12:00:00",
                raw("33333333333333333333333333333333"),
                "DEBIT",
                raw("22222222222222222222222222222222"),
                "CREDIT"
        );
    }

    private void seedFailedTransfers() {
        insertFailedTransfer(raw("51515151515151515151515151515151"), "INSUFFICIENT_FUNDS", 1000);
        insertFailedTransfer(raw("52525252525252525252525252525252"), "INSUFFICIENT_FUNDS", 500);
        insertFailedTransfer(raw("53535353535353535353535353535353"), "LIMIT_EXCEEDED", 800);
    }

    private void insertFailedTransfer(byte[] id, String failureReasonCode, long amountMinor) {
        jdbc.update("""
            insert into transfer_requests (
                id, source_account_id, destination_account_id, requested_by_customer_id,
                ledger_transaction_id, external_reference, transfer_type, requested_by_actor_type,
                status, currency_code, amount_minor, description, failure_reason_code,
                failure_reason_detail, requested_at, completed_at, created_at, updated_at, version
            ) values (
                :id, :source_account_id, :destination_account_id, :customer_id,
                null, null, 'INTERNAL', 'CUSTOMER',
                'FAILED', 'USD', :amount_minor, 'Failed report transfer', :failure_reason_code,
                'Deterministic report failure', :now, null, :now, :now, 0
            )
            """,
                params()
                        .addValue("id", id)
                        .addValue("source_account_id", raw("11111111111111111111111111111111"))
                        .addValue("destination_account_id", raw("22222222222222222222222222222222"))
                        .addValue("customer_id", raw("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                        .addValue("amount_minor", amountMinor)
                        .addValue("failure_reason_code", failureReasonCode)
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
        var localPath = Path.of("..", path).normalize();
        if (Files.exists(localPath)) {
            return Files.readString(localPath, StandardCharsets.UTF_8);
        }

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
