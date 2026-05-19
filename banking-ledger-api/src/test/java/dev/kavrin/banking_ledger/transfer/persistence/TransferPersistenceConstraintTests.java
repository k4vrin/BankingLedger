package dev.kavrin.banking_ledger.transfer.persistence;

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
class TransferPersistenceConstraintTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void externalReferenceUniquenessIsEnforced() {
        var ids = insertAccounts("USD", "USD");
        var externalReference = "transfer-unq-" + shortSuffix();
        insertPendingTransfer(UUID.randomUUID(), ids.sourceAccountId(), ids.destinationAccountId(), null, externalReference, "USD", 100);

        assertThatThrownBy(() -> insertPendingTransfer(
                UUID.randomUUID(),
                ids.sourceAccountId(),
                ids.destinationAccountId(),
                null,
                externalReference,
                "USD",
                100
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ledgerTransactionUniquenessIsEnforced() {
        var ids = insertAccounts("USD", "USD");
        var ledgerTransactionId = insertPostedLedgerTransaction("USD");
        insertCompletedTransfer(UUID.randomUUID(), ids.sourceAccountId(), ids.destinationAccountId(), ledgerTransactionId, "ledger-unq-a-" + shortSuffix(), "USD", 100);

        assertThatThrownBy(() -> insertCompletedTransfer(
                UUID.randomUUID(),
                ids.sourceAccountId(),
                ids.destinationAccountId(),
                ledgerTransactionId,
                "ledger-unq-b-" + shortSuffix(),
                "USD",
                100
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sourceAndDestinationForeignKeysAreEnforced() {
        var ids = insertAccounts("USD", "USD");

        assertThatThrownBy(() -> insertPendingTransfer(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ids.destinationAccountId(),
                null,
                "missing-source-" + shortSuffix(),
                "USD",
                100
        ))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertPendingTransfer(
                UUID.randomUUID(),
                ids.sourceAccountId(),
                UUID.randomUUID(),
                null,
                "missing-destination-" + shortSuffix(),
                "USD",
                100
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sourceAndDestinationCurrencyForeignKeysRejectMismatches() {
        var sourceMismatch = insertAccounts("EUR", "USD");
        assertThatThrownBy(() -> insertPendingTransfer(
                UUID.randomUUID(),
                sourceMismatch.sourceAccountId(),
                sourceMismatch.destinationAccountId(),
                null,
                "source-currency-mismatch-" + shortSuffix(),
                "USD",
                100
        ))
                .isInstanceOf(DataIntegrityViolationException.class);

        var destinationMismatch = insertAccounts("USD", "EUR");
        assertThatThrownBy(() -> insertPendingTransfer(
                UUID.randomUUID(),
                destinationMismatch.sourceAccountId(),
                destinationMismatch.destinationAccountId(),
                null,
                "destination-currency-mismatch-" + shortSuffix(),
                "USD",
                100
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void amountCheckRejectsNonPositiveAmounts() {
        var ids = insertAccounts("USD", "USD");

        assertThatThrownBy(() -> insertPendingTransfer(
                UUID.randomUUID(),
                ids.sourceAccountId(),
                ids.destinationAccountId(),
                null,
                "zero-amount-" + shortSuffix(),
                "USD",
                0
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void completedStatusRequiresCompletedAt() {
        var ids = insertAccounts("USD", "USD");
        var ledgerTransactionId = insertPostedLedgerTransaction("USD");

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into transfer_requests (
                    id, source_account_id, destination_account_id, ledger_transaction_id, external_reference,
                    transfer_type, requested_by_actor_type, status, currency_code, amount_minor,
                    requested_at, completed_at, created_at, updated_at, version
                ) values (
                    hextoraw(?), hextoraw(?), hextoraw(?), hextoraw(?), ?,
                    'INTERNAL', 'SYSTEM', 'COMPLETED', 'USD', 100,
                    systimestamp, null, systimestamp, systimestamp, 0
                )
                """,
                raw(UUID.randomUUID()),
                raw(ids.sourceAccountId()),
                raw(ids.destinationAccountId()),
                raw(ledgerTransactionId),
                "completed-without-time-" + shortSuffix()
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private AccountIds insertAccounts(String sourceCurrencyCode, String destinationCurrencyCode) {
        var customerId = insertCustomer();
        var sourceAccountId = UUID.randomUUID();
        var destinationAccountId = UUID.randomUUID();
        insertAccount(sourceAccountId, customerId, "SRC" + shortSuffix(), sourceCurrencyCode);
        insertAccount(destinationAccountId, customerId, "DST" + shortSuffix(), destinationCurrencyCode);
        return new AccountIds(sourceAccountId, destinationAccountId);
    }

    private UUID insertCustomer() {
        var customerId = UUID.randomUUID();
        var suffix = shortSuffix();
        jdbcTemplate.update(
                """
                insert into customers (
                    id, external_customer_reference, full_name, email, status, created_at, updated_at, version
                ) values (
                    hextoraw(?), ?, 'Transfer Constraint Customer', ?, 'ACTIVE', systimestamp, systimestamp, 0
                )
                """,
                raw(customerId),
                "transfer-constraint-cust-" + suffix,
                "transfer-constraint-" + suffix + "@example.com"
        );
        return customerId;
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

    private UUID insertPostedLedgerTransaction(String currencyCode) {
        var ledgerTransactionId = UUID.randomUUID();
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
                "transfer-ledger-" + shortSuffix(),
                currencyCode
        );
        return ledgerTransactionId;
    }

    private void insertPendingTransfer(
            UUID transferId,
            UUID sourceAccountId,
            UUID destinationAccountId,
            UUID ledgerTransactionId,
            String externalReference,
            String currencyCode,
            long amountMinor
    ) {
        jdbcTemplate.update(
                """
                insert into transfer_requests (
                    id, source_account_id, destination_account_id, ledger_transaction_id, external_reference,
                    transfer_type, requested_by_actor_type, status, currency_code, amount_minor,
                    requested_at, completed_at, created_at, updated_at, version
                ) values (
                    hextoraw(?), hextoraw(?), hextoraw(?), ?, ?,
                    'INTERNAL', 'SYSTEM', 'PENDING', ?, ?,
                    systimestamp, null, systimestamp, systimestamp, 0
                )
                """,
                raw(transferId),
                raw(sourceAccountId),
                raw(destinationAccountId),
                ledgerTransactionId == null ? null : raw(ledgerTransactionId),
                externalReference,
                currencyCode,
                amountMinor
        );
    }

    private void insertCompletedTransfer(
            UUID transferId,
            UUID sourceAccountId,
            UUID destinationAccountId,
            UUID ledgerTransactionId,
            String externalReference,
            String currencyCode,
            long amountMinor
    ) {
        jdbcTemplate.update(
                """
                insert into transfer_requests (
                    id, source_account_id, destination_account_id, ledger_transaction_id, external_reference,
                    transfer_type, requested_by_actor_type, status, currency_code, amount_minor,
                    requested_at, completed_at, created_at, updated_at, version
                ) values (
                    hextoraw(?), hextoraw(?), hextoraw(?), hextoraw(?), ?,
                    'INTERNAL', 'SYSTEM', 'COMPLETED', ?, ?,
                    systimestamp, systimestamp, systimestamp, systimestamp, 0
                )
                """,
                raw(transferId),
                raw(sourceAccountId),
                raw(destinationAccountId),
                raw(ledgerTransactionId),
                externalReference,
                currencyCode,
                amountMinor
        );
    }

    private static String raw(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    private static String shortSuffix() {
        return raw(UUID.randomUUID()).substring(0, 12);
    }

    private record AccountIds(UUID sourceAccountId, UUID destinationAccountId) {
    }
}
