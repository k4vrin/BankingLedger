package dev.kavrin.banking_ledger.adjustment.persistence;

import dev.kavrin.banking_ledger.adjustment.domain.model.AdjustmentReasonCode;
import dev.kavrin.banking_ledger.adjustment.domain.model.AdjustmentStatus;
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
class AdjustmentRequestPersistenceConstraintTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdjustmentRequestRepository adjustmentRequestRepository;

    private static String raw(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    private static String shortSuffix() {
        return raw(UUID.randomUUID()).substring(0, 12);
    }

    @Test
    void pendingAdjustmentStoresRequestMetadataAndCanBeLookedUp() {
        var adjustmentId = UUID.randomUUID();
        var ledgerTransactionId = insertPostedLedgerTransaction("adjustment-ledger-" + shortSuffix());

        jdbcTemplate.update(
                """
                insert into adjustment_requests (
                    id, ledger_transaction_id, reason_code, reason_detail,
                    requested_by_actor_type, requested_by_actor_role, requested_by_actor_id,
                    correlation_id, requested_at, completed_at, status,
                    failure_reason_code, failure_reason_detail, created_at, updated_at, version
                ) values (
                    hextoraw(?), hextoraw(?), 'MANUAL_CORRECTION', 'manual balance correction',
                    'OPS_ADMIN', 'OPS_ADMIN', 'ops-user-1',
                    'corr-adjustment', systimestamp, systimestamp, 'COMPLETED',
                    null, null, systimestamp, systimestamp, 0
                )
                """,
                raw(adjustmentId),
                raw(ledgerTransactionId)
        );

        var adjustment = adjustmentRequestRepository.findByLedgerTransaction_Id(ledgerTransactionId).orElseThrow();

        assertThat(adjustment.getId()).isEqualTo(adjustmentId);
        assertThat(adjustment.getLedgerTransaction().getId()).isEqualTo(ledgerTransactionId);
        assertThat(adjustment.getReasonCode()).isEqualTo(AdjustmentReasonCode.MANUAL_CORRECTION);
        assertThat(adjustment.getReasonDetail()).isEqualTo("manual balance correction");
        assertThat(adjustment.getRequestedByActorType().name()).isEqualTo("OPS_ADMIN");
        assertThat(adjustment.getRequestedByActorRole().name()).isEqualTo("OPS_ADMIN");
        assertThat(adjustment.getRequestedByActorId()).isEqualTo("ops-user-1");
        assertThat(adjustment.getCorrelationId()).isEqualTo("corr-adjustment");
        assertThat(adjustment.getRequestedAt()).isNotNull();
        assertThat(adjustment.getCompletedAt()).isNotNull();
        assertThat(adjustment.getStatus()).isEqualTo(AdjustmentStatus.COMPLETED);
        assertThat(adjustment.getFailureReasonCode()).isNull();
        assertThat(adjustment.getFailureReasonDetail()).isNull();
        assertThat(adjustment.getVersion()).isZero();
    }

    @Test
    void pendingAdjustmentMayBeSavedBeforeLedgerTransactionExists() {
        var adjustment = adjustmentRequestRepository.save(AdjustmentRequestEntity.builder()
                .reasonCode(AdjustmentReasonCode.RECONCILIATION)
                .reasonDetail("pending reconciliation investigation")
                .requestedByActorType(dev.kavrin.banking_ledger.transfer.domain.model.RequestedByActorType.OPS_ADMIN)
                .requestedByActorRole(dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole.OPS_ADMIN)
                .requestedByActorId("ops-user-2")
                .correlationId("corr-pending-adjustment")
                .requestedAt(java.time.OffsetDateTime.now())
                .status(AdjustmentStatus.PENDING)
                .build());

        adjustmentRequestRepository.flush();

        assertThat(adjustment.getId()).isNotNull();
        assertThat(adjustment.getLedgerTransaction()).isNull();
        assertThat(adjustment.getCompletedAt()).isNull();
    }

    @Test
    void completedAdjustmentRequiresLedgerTransactionAndCompletedTimestamp() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into adjustment_requests (
                    id, ledger_transaction_id, reason_code, requested_by_actor_type,
                    requested_at, completed_at, status, created_at, updated_at, version
                ) values (
                    hextoraw(?), null, 'MANUAL_CORRECTION', 'OPS_ADMIN',
                    systimestamp, systimestamp, 'COMPLETED', systimestamp, systimestamp, 0
                )
                """,
                raw(UUID.randomUUID())
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void failedOrRejectedAdjustmentRequiresFailureReason() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into adjustment_requests (
                    id, reason_code, requested_by_actor_type, requested_at, status,
                    created_at, updated_at, version
                ) values (
                    hextoraw(?), 'MANUAL_CORRECTION', 'OPS_ADMIN', systimestamp, 'FAILED',
                    systimestamp, systimestamp, 0
                )
                """,
                raw(UUID.randomUUID())
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ledgerTransactionCanBelongToOnlyOneAdjustment() {
        var ledgerTransactionId = insertPostedLedgerTransaction("adjustment-unique-ledger-" + shortSuffix());
        insertCompletedAdjustment(UUID.randomUUID(), ledgerTransactionId);

        assertThatThrownBy(() -> insertCompletedAdjustment(UUID.randomUUID(), ledgerTransactionId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertCompletedAdjustment(UUID adjustmentId, UUID ledgerTransactionId) {
        jdbcTemplate.update(
                """
                insert into adjustment_requests (
                    id, ledger_transaction_id, reason_code, requested_by_actor_type,
                    requested_at, completed_at, status, created_at, updated_at, version
                ) values (
                    hextoraw(?), hextoraw(?), 'MANUAL_CORRECTION', 'OPS_ADMIN',
                    systimestamp, systimestamp, 'COMPLETED', systimestamp, systimestamp, 0
                )
                """,
                raw(adjustmentId),
                raw(ledgerTransactionId)
        );
    }

    private UUID insertPostedLedgerTransaction(String externalReference) {
        var ledgerTransactionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into ledger_transactions (
                    id, external_reference, transaction_type, status, currency_code, amount_minor,
                    posted_at, created_at, updated_at, version
                ) values (
                    hextoraw(?), ?, 'ADJUSTMENT', 'POSTED', 'USD', 100,
                    systimestamp, systimestamp, systimestamp, 0
                )
                """,
                raw(ledgerTransactionId),
                externalReference
        );
        return ledgerTransactionId;
    }
}
