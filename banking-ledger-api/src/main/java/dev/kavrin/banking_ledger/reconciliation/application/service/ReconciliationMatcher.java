package dev.kavrin.banking_ledger.reconciliation.application.service;

import dev.kavrin.banking_ledger.ledger.domain.model.TransactionStatus;
import dev.kavrin.banking_ledger.ledger.persistence.entity.LedgerTransactionEntity;
import dev.kavrin.banking_ledger.ledger.persistence.repository.LedgerTransactionRepository;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationMismatchType;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationResultStatus;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationSeverity;
import dev.kavrin.banking_ledger.reconciliation.domain.model.SettlementItemStatus;
import dev.kavrin.banking_ledger.reconciliation.persistence.ReconciliationResultEntity;
import dev.kavrin.banking_ledger.reconciliation.persistence.SettlementBatchEntity;
import dev.kavrin.banking_ledger.reconciliation.persistence.SettlementItemEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReconciliationMatcher {

    private final LedgerTransactionRepository ledgerTransactionRepository;

    public List<ReconciliationResultEntity> match(
            SettlementBatchEntity batch,
            List<SettlementItemEntity> items
    ) {
        var externalReferences = items.stream()
                .map(SettlementItemEntity::getExternalTransactionReference)
                .toList();
        Map<String, List<LedgerTransactionEntity>> ledgersByReference =
                ledgerTransactionRepository.findByExternalReferenceIn(externalReferences)
                        .stream()
                        .collect(Collectors.groupingBy(LedgerTransactionEntity::getExternalReference));

        var results = new ArrayList<ReconciliationResultEntity>();
        for (SettlementItemEntity item : items) {
            results.add(matchItem(batch, item, ledgersByReference.getOrDefault(
                    item.getExternalTransactionReference(),
                    List.of()
            )));
        }
        results.addAll(missingExternalResults(batch, items, externalReferences));
        return results;
    }

    private ReconciliationResultEntity matchItem(
            SettlementBatchEntity batch,
            SettlementItemEntity item,
            List<LedgerTransactionEntity> ledgers
    ) {
        if (ledgers.size() > 1) {
            return result(
                    batch,
                    item,
                    ledgers.getFirst(),
                    ReconciliationMismatchType.DUPLICATE_INTERNAL_TRANSACTION,
                    ReconciliationSeverity.CRITICAL,
                    "Multiple internal ledger transactions exist for external reference " + item.getExternalTransactionReference()
            );
        }

        LedgerTransactionEntity ledger = ledgers.isEmpty() ? null : ledgers.getFirst();
        if (ledger == null) {
            return result(
                    batch,
                    item,
                    null,
                    ReconciliationMismatchType.MISSING_INTERNAL_TRANSACTION,
                    ReconciliationSeverity.CRITICAL,
                    "No internal ledger transaction exists for external reference " + item.getExternalTransactionReference()
            );
        }

        if (ledger.getStatus() == TransactionStatus.REVERSED && item.getStatus() == SettlementItemStatus.SETTLED) {
            return result(
                    batch,
                    item,
                    ledger,
                    ReconciliationMismatchType.REVERSED_TRANSACTION_SETTLED,
                    ReconciliationSeverity.CRITICAL,
                    "Reversed ledger transaction was externally settled as successful."
            );
        }

        if (ledger.getAmountMinor() != item.getAmountMinor()) {
            return result(
                    batch,
                    item,
                    ledger,
                    ReconciliationMismatchType.AMOUNT_MISMATCH,
                    ReconciliationSeverity.CRITICAL,
                    "Settlement amount does not match internal ledger amount."
            );
        }

        if (!ledger.getCurrencyCode().equals(item.getCurrencyCode())) {
            return result(
                    batch,
                    item,
                    ledger,
                    ReconciliationMismatchType.CURRENCY_MISMATCH,
                    ReconciliationSeverity.CRITICAL,
                    "Settlement currency does not match internal ledger currency."
            );
        }

        if (item.getStatus() == SettlementItemStatus.SETTLED && ledger.getStatus() != TransactionStatus.POSTED) {
            return result(
                    batch,
                    item,
                    ledger,
                    ReconciliationMismatchType.STATUS_MISMATCH,
                    ReconciliationSeverity.WARNING,
                    "Settled external item does not match an internally posted transaction."
            );
        }

        if (item.getStatus() != SettlementItemStatus.SETTLED && ledger.getStatus() == TransactionStatus.POSTED) {
            return result(
                    batch,
                    item,
                    ledger,
                    ReconciliationMismatchType.STATUS_MISMATCH,
                    ReconciliationSeverity.WARNING,
                    "Internal transaction is posted but external settlement status is " + item.getStatus() + "."
            );
        }

        if (ledger.getPostedAt() != null
                && !ledger.getPostedAt().toLocalDate().equals(item.getSettlementDate())) {
            return result(
                    batch,
                    item,
                    ledger,
                    ReconciliationMismatchType.SETTLEMENT_DATE_OUT_OF_WINDOW,
                    ReconciliationSeverity.WARNING,
                    "Settlement date does not match internal ledger posting date."
            );
        }

        return result(
                batch,
                item,
                ledger,
                ReconciliationMismatchType.MATCHED,
                ReconciliationSeverity.INFO,
                "Settlement item matches internal ledger transaction."
        );
    }

    private List<ReconciliationResultEntity> missingExternalResults(
            SettlementBatchEntity batch,
            List<SettlementItemEntity> items,
            List<String> externalReferences
    ) {
        if (items.isEmpty()) {
            return List.of();
        }
        var minDate = items.stream().map(SettlementItemEntity::getSettlementDate).min(LocalDate::compareTo).orElseThrow();
        var maxDate = items.stream().map(SettlementItemEntity::getSettlementDate).max(LocalDate::compareTo).orElseThrow();
        OffsetDateTime from = minDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = maxDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        return ledgerTransactionRepository
                .findByStatusInAndPostedAtGreaterThanEqualAndPostedAtLessThanAndExternalReferenceNotInOrderByExternalReferenceAscIdAsc(
                        List.of(TransactionStatus.POSTED, TransactionStatus.REVERSED),
                        from,
                        to,
                        externalReferences
                )
                .stream()
                .map(ledger -> result(
                        batch,
                        null,
                        ledger,
                        ReconciliationMismatchType.MISSING_EXTERNAL_SETTLEMENT,
                        ReconciliationSeverity.CRITICAL,
                        "No external settlement item exists for internal reference " + ledger.getExternalReference()
                ))
                .toList();
    }

    private ReconciliationResultEntity result(
            SettlementBatchEntity batch,
            SettlementItemEntity item,
            LedgerTransactionEntity ledger,
            ReconciliationMismatchType mismatchType,
            ReconciliationSeverity severity,
            String detail
    ) {
        boolean matched = mismatchType == ReconciliationMismatchType.MATCHED;
        var now = OffsetDateTime.now();
        return ReconciliationResultEntity.builder()
                .batch(batch)
                .item(item)
                .ledgerTransaction(ledger)
                .mismatchType(mismatchType)
                .severity(severity)
                .status(matched ? ReconciliationResultStatus.RESOLVED : ReconciliationResultStatus.OPEN)
                .detail(detail)
                .createdAt(now)
                .resolvedAt(matched ? now : null)
                .build();
    }
}
