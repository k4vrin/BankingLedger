package dev.kavrin.banking_ledger.transfer.api.dto;

import dev.kavrin.banking_ledger.transfer.domain.model.TransferStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID sourceAccountId,
        UUID destinationAccountId,
        TransferStatus status,
        String currencyCode,
        long amountMinor,
        UUID ledgerTransactionId,
        String externalReference,
        String description,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String failureReasonCode,
        String failureReasonDetail
) {
}
