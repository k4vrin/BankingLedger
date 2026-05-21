package dev.kavrin.banking_ledger.reconciliation.application.service;

import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.reconciliation.api.dto.CreateSettlementBatchRequest;
import dev.kavrin.banking_ledger.reconciliation.api.dto.SettlementItemRequest;
import dev.kavrin.banking_ledger.reconciliation.application.command.CreateSettlementBatchCommand;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationMismatchType;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationResultStatus;
import dev.kavrin.banking_ledger.reconciliation.domain.model.SettlementBatchStatus;
import dev.kavrin.banking_ledger.reconciliation.persistence.ReconciliationResultRepository;
import dev.kavrin.banking_ledger.reconciliation.persistence.SettlementBatchRepository;
import dev.kavrin.banking_ledger.reconciliation.persistence.SettlementItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CreateSettlementBatchUseCaseIntegrationTest {

    @Autowired
    private CreateSettlementBatchUseCase useCase;

    @Autowired
    private SettlementBatchRepository batchRepository;

    @Autowired
    private SettlementItemRepository itemRepository;

    @Autowired
    private ReconciliationResultRepository resultRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.getJdbcTemplate().execute("delete from outbox_events");
        jdbc.getJdbcTemplate().execute("delete from audit_events");
        jdbc.getJdbcTemplate().execute("delete from reconciliation_results");
        jdbc.getJdbcTemplate().execute("delete from settlement_items");
        jdbc.getJdbcTemplate().execute("delete from settlement_batches");
        jdbc.getJdbcTemplate().execute("delete from reversals");
        jdbc.getJdbcTemplate().execute("delete from adjustment_requests");
        jdbc.getJdbcTemplate().execute("delete from transfer_requests");
        jdbc.getJdbcTemplate().execute("delete from postings");
        jdbc.getJdbcTemplate().execute("delete from journal_entries");
        jdbc.getJdbcTemplate().execute("delete from ledger_transactions");
    }

    @Test
    void importPersistsBatchItemsResultsAndIncrementsBatchVersion() {
        insertLedgerTransaction("EXT-P10-1", 1000, "POSTED");

        var response = useCase.handle(command("processor-phase10", "phase10.csv", "EXT-P10-1", 1000));

        assertThat(response.status()).isEqualTo(SettlementBatchStatus.COMPLETED);
        assertThat(response.itemCount()).isEqualTo(1);
        assertThat(response.matchedCount()).isEqualTo(1);
        assertThat(response.mismatchCount()).isEqualTo(0);

        var persistedBatch = batchRepository.findById(response.id()).orElseThrow();
        assertThat(persistedBatch.getSource()).isEqualTo("processor-phase10");
        assertThat(persistedBatch.getImportedByActor()).isEqualTo("ops-user-1");
        assertThat(persistedBatch.getCorrelationId()).isEqualTo("corr-phase10");
        assertThat(persistedBatch.getStatus()).isEqualTo(SettlementBatchStatus.COMPLETED);
        assertThat(persistedBatch.getVersion()).isGreaterThanOrEqualTo(1);

        var items = itemRepository.findByBatch_IdOrderByExternalTransactionReferenceAscIdAsc(response.id());
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getExternalTransactionReference()).isEqualTo("EXT-P10-1");

        var results = resultRepository.findByBatch_IdOrderByCreatedAtDescIdDesc(response.id(), org.springframework.data.domain.Pageable.unpaged());
        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().getFirst().getMismatchType()).isEqualTo(ReconciliationMismatchType.MATCHED);
        assertThat(results.getContent().getFirst().getStatus()).isEqualTo(ReconciliationResultStatus.RESOLVED);
        assertThat(results.getContent().getFirst().getResolvedAt()).isNotNull();
    }

    @Test
    void duplicateSourceAndExternalReferenceRollsBackSecondImport() {
        insertLedgerTransaction("EXT-P10-DUP", 1000, "POSTED");
        useCase.handle(command("processor-phase10", "first.csv", "EXT-P10-DUP", 1000));

        assertThatThrownBy(() -> useCase.handle(command("processor-phase10", "second.csv", "EXT-P10-DUP", 1000)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(batchRepository.count()).isEqualTo(1);
        assertThat(itemRepository.count()).isEqualTo(1);
        assertThat(resultRepository.count()).isEqualTo(1);
    }

    private CreateSettlementBatchCommand command(
            String source,
            String referenceName,
            String externalReference,
            long amountMinor
    ) {
        var request = new CreateSettlementBatchRequest(
                source,
                referenceName,
                "ignored-request-actor",
                "ignored-request-correlation",
                List.of(new SettlementItemRequest(
                        externalReference,
                        amountMinor,
                        "USD",
                        "SETTLED",
                        LocalDate.of(2026, 5, 20),
                        Map.of("line", externalReference)
                ))
        );
        return new CreateSettlementBatchCommand(
                request,
                AuditActorType.EMPLOYEE,
                AuditActorRole.OPS_ADMIN,
                "ops-user-1",
                "corr-phase10"
        );
    }

    private void insertLedgerTransaction(String externalReference, long amountMinor, String status) {
        jdbc.update("""
            insert into ledger_transactions (
                id, external_reference, transaction_type, status, currency_code, amount_minor,
                description, posted_at, created_at, updated_at, version
            ) values (
                sys_guid(), :external_reference, 'TRANSFER', :status, 'USD', :amount_minor,
                'Phase 10 integration ledger transaction', :posted_at, :posted_at, :posted_at, 0
            )
            """,
                new MapSqlParameterSource()
                        .addValue("external_reference", externalReference)
                        .addValue("status", status)
                        .addValue("amount_minor", amountMinor)
                        .addValue("posted_at", Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 12, 0)))
        );
    }
}
