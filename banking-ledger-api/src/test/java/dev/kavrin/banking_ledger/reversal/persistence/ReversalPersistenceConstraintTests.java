package dev.kavrin.banking_ledger.reversal.persistence;

import dev.kavrin.banking_ledger.reversal.domain.model.ReversalReasonCode;
import dev.kavrin.banking_ledger.reversal.domain.model.ReversalStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ReversalPersistenceConstraintTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReversalRepository reversalRepository;

    private static String raw(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    private static String shortSuffix() {
        return raw(UUID.randomUUID()).substring(0, 12);
    }

    @Test
    void pendingReversalStoresRequiredDataModelFieldsAndCanBeLookedUp() {
        var ids = insertCompletedTransferGraph("USD");
        var reversalId = UUID.randomUUID();

        insertPendingReversal(reversalId, ids.transferId(), ids.originalLedgerTransactionId(), "corr-reversal");

        var reversal = reversalRepository.findByOriginalTransfer_Id(ids.transferId()).orElseThrow();
        assertThat(reversal.getId()).isEqualTo(reversalId);
        assertThat(reversal.getOriginalTransfer().getId()).isEqualTo(ids.transferId());
        assertThat(reversal.getOriginalLedgerTransaction().getId()).isEqualTo(ids.originalLedgerTransactionId());
        assertThat(reversal.getReversalLedgerTransaction()).isNull();
        assertThat(reversal.getReasonCode()).isEqualTo(ReversalReasonCode.DUPLICATE_TRANSFER);
        assertThat(reversal.getReasonDetail()).isEqualTo("duplicate mobile submission");
        assertThat(reversal.getRequestedByActorType().name()).isEqualTo("OPS_ADMIN");
        assertThat(reversal.getRequestedByActorRole().name()).isEqualTo("OPS_ADMIN");
        assertThat(reversal.getRequestedByActorId()).isEqualTo("ops-user-1");
        assertThat(reversal.getCorrelationId()).isEqualTo("corr-reversal");
        assertThat(reversal.getRequestedAt()).isNotNull();
        assertThat(reversal.getCompletedAt()).isNull();
        assertThat(reversal.getStatus()).isEqualTo(ReversalStatus.PENDING);
        assertThat(reversal.getFailureReasonCode()).isNull();
        assertThat(reversal.getFailureReasonDetail()).isNull();
        assertThat(reversal.getVersion()).isZero();

        assertThat(reversalRepository.findByOriginalLedgerTransaction_Id(ids.originalLedgerTransactionId()))
                .isPresent();
    }

    @Test
    void oneReversalPerOriginalTransferAndLedgerTransactionIsEnforced() {
        var ids = insertCompletedTransferGraph("USD");
        insertPendingReversal(UUID.randomUUID(), ids.transferId(), ids.originalLedgerTransactionId(), "corr-one");

        assertThatThrownBy(() -> insertPendingReversal(
                UUID.randomUUID(),
                ids.transferId(),
                ids.originalLedgerTransactionId(),
                "corr-two"
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void completedReversalRequiresCompletedTimestampAndReversalLedgerTransaction() {
        var ids = insertCompletedTransferGraph("USD");

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into reversals (
                    id, original_transfer_id, original_ledger_transaction_id, reversal_ledger_transaction_id,
                    reason_code, requested_by_actor_type, requested_at, completed_at, status,
                    created_at, updated_at, version
                ) values (
                    hextoraw(?), hextoraw(?), hextoraw(?), null,
                    'DUPLICATE_TRANSFER', 'OPS_ADMIN', systimestamp, null, 'COMPLETED',
                    systimestamp, systimestamp, 0
                )
                """,
                raw(UUID.randomUUID()),
                raw(ids.transferId()),
                raw(ids.originalLedgerTransactionId())
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void failedOrRejectedReversalRequiresFailureReason() {
        var ids = insertCompletedTransferGraph("USD");

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into reversals (
                    id, original_transfer_id, original_ledger_transaction_id,
                    reason_code, requested_by_actor_type, requested_at, status,
                    created_at, updated_at, version
                ) values (
                    hextoraw(?), hextoraw(?), hextoraw(?),
                    'DUPLICATE_TRANSFER', 'OPS_ADMIN', systimestamp, 'FAILED',
                    systimestamp, systimestamp, 0
                )
                """,
                raw(UUID.randomUUID()),
                raw(ids.transferId()),
                raw(ids.originalLedgerTransactionId())
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void completedReversalStoresUniqueReversalLedgerTransaction() {
        var ids = insertCompletedTransferGraph("USD");
        var reversalLedgerTransactionId = insertPostedLedgerTransaction("reversal-ledger-" + shortSuffix(), "REVERSAL", "USD");

        jdbcTemplate.update(
                """
                insert into reversals (
                    id, original_transfer_id, original_ledger_transaction_id, reversal_ledger_transaction_id,
                    reason_code, requested_by_actor_type, requested_at, completed_at, status,
                    created_at, updated_at, version
                ) values (
                    hextoraw(?), hextoraw(?), hextoraw(?), hextoraw(?),
                    'DUPLICATE_TRANSFER', 'OPS_ADMIN', systimestamp, systimestamp, 'COMPLETED',
                    systimestamp, systimestamp, 0
                )
                """,
                raw(UUID.randomUUID()),
                raw(ids.transferId()),
                raw(ids.originalLedgerTransactionId()),
                raw(reversalLedgerTransactionId)
        );

        var otherIds = insertCompletedTransferGraph("USD");
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into reversals (
                    id, original_transfer_id, original_ledger_transaction_id, reversal_ledger_transaction_id,
                    reason_code, requested_by_actor_type, requested_at, completed_at, status,
                    created_at, updated_at, version
                ) values (
                    hextoraw(?), hextoraw(?), hextoraw(?), hextoraw(?),
                    'DUPLICATE_TRANSFER', 'OPS_ADMIN', systimestamp, systimestamp, 'COMPLETED',
                    systimestamp, systimestamp, 0
                )
                """,
                raw(UUID.randomUUID()),
                raw(otherIds.transferId()),
                raw(otherIds.originalLedgerTransactionId()),
                raw(reversalLedgerTransactionId)
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private TransferGraphIds insertCompletedTransferGraph(String currencyCode) {
        var customerId = insertCustomer();
        var sourceAccountId = UUID.randomUUID();
        var destinationAccountId = UUID.randomUUID();
        var ledgerTransactionId = insertPostedLedgerTransaction("original-ledger-" + shortSuffix(), "TRANSFER", currencyCode);
        var transferId = UUID.randomUUID();

        insertAccount(sourceAccountId, customerId, "REV-SRC-" + shortSuffix(), currencyCode);
        insertAccount(destinationAccountId, customerId, "REV-DST-" + shortSuffix(), currencyCode);

        jdbcTemplate.update(
                """
                insert into transfer_requests (
                    id, source_account_id, destination_account_id, ledger_transaction_id, external_reference,
                    transfer_type, requested_by_actor_type, status, currency_code, amount_minor,
                    requested_at, completed_at, created_at, updated_at, version
                ) values (
                    hextoraw(?), hextoraw(?), hextoraw(?), hextoraw(?), ?,
                    'INTERNAL', 'SYSTEM', 'COMPLETED', ?, 100,
                    systimestamp, systimestamp, systimestamp, systimestamp, 0
                )
                """,
                raw(transferId),
                raw(sourceAccountId),
                raw(destinationAccountId),
                raw(ledgerTransactionId),
                "reversal-transfer-" + shortSuffix(),
                currencyCode
        );

        return new TransferGraphIds(transferId, ledgerTransactionId);
    }

    private UUID insertCustomer() {
        var customerId = UUID.randomUUID();
        var suffix = shortSuffix();
        jdbcTemplate.update(
                """
                insert into customers (
                    id, external_customer_reference, full_name, email, status, created_at, updated_at, version
                ) values (
                    hextoraw(?), ?, 'Reversal Constraint Customer', ?, 'ACTIVE', systimestamp, systimestamp, 0
                )
                """,
                raw(customerId),
                "reversal-cust-" + suffix,
                "reversal-" + suffix + "@example.com"
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

    private UUID insertPostedLedgerTransaction(String externalReference, String transactionType, String currencyCode) {
        var ledgerTransactionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into ledger_transactions (
                    id, external_reference, transaction_type, status, currency_code, amount_minor,
                    posted_at, created_at, updated_at, version
                ) values (
                    hextoraw(?), ?, ?, 'POSTED', ?, 100,
                    systimestamp, systimestamp, systimestamp, 0
                )
                """,
                raw(ledgerTransactionId),
                externalReference,
                transactionType,
                currencyCode
        );
        return ledgerTransactionId;
    }

    private void insertPendingReversal(
            UUID reversalId,
            UUID originalTransferId,
            UUID originalLedgerTransactionId,
            String correlationId
    ) {
        jdbcTemplate.update(
                """
                insert into reversals (
                    id, original_transfer_id, original_ledger_transaction_id,
                    reason_code, reason_detail, requested_by_actor_type, requested_by_actor_role,
                    requested_by_actor_id, correlation_id, requested_at, status,
                    created_at, updated_at, version
                ) values (
                    hextoraw(?), hextoraw(?), hextoraw(?),
                    'DUPLICATE_TRANSFER', 'duplicate mobile submission', 'OPS_ADMIN', 'OPS_ADMIN',
                    'ops-user-1', ?, systimestamp, 'PENDING',
                    systimestamp, systimestamp, 0
                )
                """,
                raw(reversalId),
                raw(originalTransferId),
                raw(originalLedgerTransactionId),
                correlationId
        );
    }

    private record TransferGraphIds(UUID transferId, UUID originalLedgerTransactionId) {
    }
}
