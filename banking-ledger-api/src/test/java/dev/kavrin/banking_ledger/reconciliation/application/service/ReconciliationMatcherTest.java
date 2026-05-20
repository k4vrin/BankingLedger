package dev.kavrin.banking_ledger.reconciliation.application.service;

import dev.kavrin.banking_ledger.ledger.domain.model.LedgerTransactionType;
import dev.kavrin.banking_ledger.ledger.domain.model.TransactionStatus;
import dev.kavrin.banking_ledger.ledger.persistence.entity.LedgerTransactionEntity;
import dev.kavrin.banking_ledger.ledger.persistence.repository.LedgerTransactionRepository;
import dev.kavrin.banking_ledger.reconciliation.domain.model.*;
import dev.kavrin.banking_ledger.reconciliation.persistence.SettlementBatchEntity;
import dev.kavrin.banking_ledger.reconciliation.persistence.SettlementItemEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReconciliationMatcherTest {

    private final LedgerTransactionRepository ledgerTransactionRepository = mock(LedgerTransactionRepository.class);
    private final ReconciliationMatcher matcher = new ReconciliationMatcher(ledgerTransactionRepository);

    @Test
    void exactMatchProducesResolvedInfoResult() {
        var item = item("ext-1", 100, "USD", SettlementItemStatus.SETTLED);
        var ledger = ledger("ext-1", 100, "USD", TransactionStatus.POSTED);
        when(ledgerTransactionRepository.findByExternalReferenceIn(List.of("ext-1"))).thenReturn(List.of(ledger));
        when(ledgerTransactionRepository.findByStatusInAndPostedAtGreaterThanEqualAndPostedAtLessThanAndExternalReferenceNotInOrderByExternalReferenceAscIdAsc(any(), any(), any(), any()))
                .thenReturn(List.of());

        var results = matcher.match(batch(), List.of(item));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getMismatchType()).isEqualTo(ReconciliationMismatchType.MATCHED);
        assertThat(results.getFirst().getSeverity()).isEqualTo(ReconciliationSeverity.INFO);
        assertThat(results.getFirst().getStatus()).isEqualTo(ReconciliationResultStatus.RESOLVED);
        assertThat(results.getFirst().getResolvedAt()).isNotNull();
    }

    @Test
    void missingInternalTransactionIsCritical() {
        var item = item("ext-1", 100, "USD", SettlementItemStatus.SETTLED);
        when(ledgerTransactionRepository.findByExternalReferenceIn(List.of("ext-1"))).thenReturn(List.of());
        when(ledgerTransactionRepository.findByStatusInAndPostedAtGreaterThanEqualAndPostedAtLessThanAndExternalReferenceNotInOrderByExternalReferenceAscIdAsc(any(), any(), any(), any()))
                .thenReturn(List.of());

        var result = matcher.match(batch(), List.of(item)).getFirst();

        assertThat(result.getMismatchType()).isEqualTo(ReconciliationMismatchType.MISSING_INTERNAL_TRANSACTION);
        assertThat(result.getSeverity()).isEqualTo(ReconciliationSeverity.CRITICAL);
        assertThat(result.getStatus()).isEqualTo(ReconciliationResultStatus.OPEN);
    }

    @Test
    void amountMismatchIsCritical() {
        var result = singleResult(item("ext-1", 100, "USD", SettlementItemStatus.SETTLED),
                ledger("ext-1", 101, "USD", TransactionStatus.POSTED));

        assertThat(result.getMismatchType()).isEqualTo(ReconciliationMismatchType.AMOUNT_MISMATCH);
        assertThat(result.getSeverity()).isEqualTo(ReconciliationSeverity.CRITICAL);
    }

    @Test
    void currencyMismatchIsCritical() {
        var result = singleResult(item("ext-1", 100, "USD", SettlementItemStatus.SETTLED),
                ledger("ext-1", 100, "EUR", TransactionStatus.POSTED));

        assertThat(result.getMismatchType()).isEqualTo(ReconciliationMismatchType.CURRENCY_MISMATCH);
        assertThat(result.getSeverity()).isEqualTo(ReconciliationSeverity.CRITICAL);
    }

    @Test
    void statusMismatchIsWarning() {
        var result = singleResult(item("ext-1", 100, "USD", SettlementItemStatus.REJECTED),
                ledger("ext-1", 100, "USD", TransactionStatus.POSTED));

        assertThat(result.getMismatchType()).isEqualTo(ReconciliationMismatchType.STATUS_MISMATCH);
        assertThat(result.getSeverity()).isEqualTo(ReconciliationSeverity.WARNING);
    }

    @Test
    void reversedTransactionSettledExternallyIsCritical() {
        var result = singleResult(item("ext-1", 100, "USD", SettlementItemStatus.SETTLED),
                ledger("ext-1", 100, "USD", TransactionStatus.REVERSED));

        assertThat(result.getMismatchType()).isEqualTo(ReconciliationMismatchType.REVERSED_TRANSACTION_SETTLED);
        assertThat(result.getSeverity()).isEqualTo(ReconciliationSeverity.CRITICAL);
    }

    @Test
    void missingExternalSettlementIsCritical() {
        var item = item("ext-1", 100, "USD", SettlementItemStatus.SETTLED);
        var missingLedger = ledger("ext-2", 100, "USD", TransactionStatus.POSTED);
        when(ledgerTransactionRepository.findByExternalReferenceIn(List.of("ext-1"))).thenReturn(List.of(ledger("ext-1", 100, "USD", TransactionStatus.POSTED)));
        when(ledgerTransactionRepository.findByStatusInAndPostedAtGreaterThanEqualAndPostedAtLessThanAndExternalReferenceNotInOrderByExternalReferenceAscIdAsc(any(), any(), any(), any()))
                .thenReturn(List.of(missingLedger));

        var results = matcher.match(batch(), List.of(item));

        assertThat(results).anySatisfy(result -> {
            assertThat(result.getMismatchType()).isEqualTo(ReconciliationMismatchType.MISSING_EXTERNAL_SETTLEMENT);
            assertThat(result.getSeverity()).isEqualTo(ReconciliationSeverity.CRITICAL);
            assertThat(result.getLedgerTransaction()).isEqualTo(missingLedger);
        });
    }

    @Test
    void duplicateInternalReferenceIsCritical() {
        var item = item("ext-1", 100, "USD", SettlementItemStatus.SETTLED);
        when(ledgerTransactionRepository.findByExternalReferenceIn(List.of("ext-1")))
                .thenReturn(List.of(
                        ledger("ext-1", 100, "USD", TransactionStatus.POSTED),
                        ledger("ext-1", 100, "USD", TransactionStatus.POSTED)
                ));
        when(ledgerTransactionRepository.findByStatusInAndPostedAtGreaterThanEqualAndPostedAtLessThanAndExternalReferenceNotInOrderByExternalReferenceAscIdAsc(any(), any(), any(), any()))
                .thenReturn(List.of());

        var result = matcher.match(batch(), List.of(item)).getFirst();

        assertThat(result.getMismatchType()).isEqualTo(ReconciliationMismatchType.DUPLICATE_INTERNAL_TRANSACTION);
        assertThat(result.getSeverity()).isEqualTo(ReconciliationSeverity.CRITICAL);
    }

    @Test
    void settlementDateOutsideLedgerPostingDateIsWarning() {
        var item = item("ext-1", 100, "USD", SettlementItemStatus.SETTLED);
        var ledger = ledger("ext-1", 100, "USD", TransactionStatus.POSTED);
        ledger.setPostedAt(OffsetDateTime.parse("2026-05-21T10:15:30Z"));

        var result = singleResult(item, ledger);

        assertThat(result.getMismatchType()).isEqualTo(ReconciliationMismatchType.SETTLEMENT_DATE_OUT_OF_WINDOW);
        assertThat(result.getSeverity()).isEqualTo(ReconciliationSeverity.WARNING);
    }

    private dev.kavrin.banking_ledger.reconciliation.persistence.ReconciliationResultEntity singleResult(
            SettlementItemEntity item,
            LedgerTransactionEntity ledger
    ) {
        when(ledgerTransactionRepository.findByExternalReferenceIn(List.of(item.getExternalTransactionReference())))
                .thenReturn(List.of(ledger));
        when(ledgerTransactionRepository.findByStatusInAndPostedAtGreaterThanEqualAndPostedAtLessThanAndExternalReferenceNotInOrderByExternalReferenceAscIdAsc(any(), any(), any(), any()))
                .thenReturn(List.of());

        return matcher.match(batch(), List.of(item)).getFirst();
    }

    private SettlementBatchEntity batch() {
        return SettlementBatchEntity.builder()
                .id(UUID.randomUUID())
                .source("VISA")
                .referenceName("settlement.csv")
                .importedByActor("ops-1")
                .status(SettlementBatchStatus.IMPORTED)
                .importedAt(OffsetDateTime.now())
                .itemCount(1)
                .build();
    }

    private SettlementItemEntity item(
            String externalReference,
            long amountMinor,
            String currencyCode,
            SettlementItemStatus status
    ) {
        return SettlementItemEntity.builder()
                .id(UUID.randomUUID())
                .externalTransactionReference(externalReference)
                .amountMinor(amountMinor)
                .currencyCode(currencyCode)
                .status(status)
                .settlementDate(LocalDate.of(2026, 5, 20))
                .build();
    }

    private LedgerTransactionEntity ledger(
            String externalReference,
            long amountMinor,
            String currencyCode,
            TransactionStatus status
    ) {
        return LedgerTransactionEntity.builder()
                .id(UUID.randomUUID())
                .externalReference(externalReference)
                .transactionType(LedgerTransactionType.TRANSFER)
                .status(status)
                .amountMinor(amountMinor)
                .currencyCode(currencyCode)
                .postedAt(OffsetDateTime.parse("2026-05-20T10:15:30Z"))
                .build();
    }
}
