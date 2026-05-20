package dev.kavrin.banking_ledger.reconciliation.application.service;

import dev.kavrin.banking_ledger.reconciliation.api.dto.ReconciliationResultSummaryResponse;
import dev.kavrin.banking_ledger.reconciliation.api.dto.SettlementBatchResponse;
import dev.kavrin.banking_ledger.reconciliation.api.dto.SettlementItemResponse;
import dev.kavrin.banking_ledger.reconciliation.persistence.ReconciliationResultEntity;
import dev.kavrin.banking_ledger.reconciliation.persistence.SettlementBatchEntity;
import dev.kavrin.banking_ledger.reconciliation.persistence.SettlementItemEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ReconciliationResponseMapper {

    public SettlementBatchResponse toResponse(
            SettlementBatchEntity batch,
            List<SettlementItemEntity> items,
            List<ReconciliationResultEntity> results
    ) {
        return new SettlementBatchResponse(
                batch.getId(),
                batch.getSource(),
                batch.getReferenceName(),
                batch.getImportedByActor(),
                batch.getCorrelationId(),
                batch.getStatus(),
                batch.getImportedAt(),
                batch.getCompletedAt(),
                batch.getItemCount(),
                batch.getMatchedCount(),
                batch.getMismatchCount(),
                items.stream().map(this::toItemResponse).toList(),
                results.stream().map(this::toResultResponse).toList()
        );
    }

    public SettlementItemResponse toItemResponse(SettlementItemEntity item) {
        return new SettlementItemResponse(
                item.getId(),
                item.getBatch().getId(),
                item.getExternalTransactionReference(),
                item.getAmountMinor(),
                item.getCurrencyCode(),
                item.getStatus(),
                item.getSettlementDate()
        );
    }

    public ReconciliationResultSummaryResponse toResultResponse(ReconciliationResultEntity result) {
        UUID itemId = result.getItem() == null ? null : result.getItem().getId();
        UUID ledgerTransactionId = result.getLedgerTransaction() == null ? null : result.getLedgerTransaction().getId();
        return new ReconciliationResultSummaryResponse(
                result.getId(),
                result.getBatch().getId(),
                itemId,
                ledgerTransactionId,
                result.getMismatchType(),
                result.getSeverity(),
                result.getStatus(),
                result.getDetail(),
                result.getCreatedAt(),
                result.getResolvedAt()
        );
    }
}
