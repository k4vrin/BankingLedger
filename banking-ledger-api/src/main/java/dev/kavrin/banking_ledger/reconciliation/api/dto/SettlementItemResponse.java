package dev.kavrin.banking_ledger.reconciliation.api.dto;

import dev.kavrin.banking_ledger.reconciliation.domain.model.SettlementItemStatus;

import java.time.LocalDate;
import java.util.UUID;

public record SettlementItemResponse(
        UUID id,
        UUID batchId,
        String externalTransactionReference,
        long amountMinor,
        String currencyCode,
        SettlementItemStatus status,
        LocalDate settlementDate
) {
}
