package dev.kavrin.banking_ledger.ledger.api.dto;

import dev.kavrin.banking_ledger.adjustment.domain.model.AdjustmentReasonCode;
import dev.kavrin.banking_ledger.adjustment.domain.model.AdjustmentStatus;
import dev.kavrin.banking_ledger.ledger.domain.model.JournalEntryType;
import dev.kavrin.banking_ledger.ledger.domain.model.LedgerTransactionType;
import dev.kavrin.banking_ledger.ledger.domain.model.PostingDirection;
import dev.kavrin.banking_ledger.ledger.domain.model.TransactionStatus;
import dev.kavrin.banking_ledger.outbox.OutboxStatus;
import dev.kavrin.banking_ledger.reversal.domain.model.ReversalReasonCode;
import dev.kavrin.banking_ledger.reversal.domain.model.ReversalStatus;
import dev.kavrin.banking_ledger.transfer.domain.model.TransferStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LedgerTransactionInvestigationResponse(
        LedgerTransactionSummary transaction,
        JournalEntrySummary journalEntry,
        List<PostingSummary> postings,
        TransferSummary transfer,
        ReversalSummary reversal,
        AdjustmentSummary adjustment,
        List<UUID> auditEventIds,
        List<OutboxEventSummary> outboxEvents
) {
    public record LedgerTransactionSummary(
            UUID id,
            String externalReference,
            LedgerTransactionType transactionType,
            TransactionStatus status,
            String currencyCode,
            long amountMinor,
            String description,
            OffsetDateTime postedAt,
            OffsetDateTime createdAt
    ) {
    }

    public record JournalEntrySummary(
            UUID id,
            JournalEntryType entryType,
            String currencyCode,
            long totalDebitMinor,
            long totalCreditMinor,
            String description,
            OffsetDateTime postedAt
    ) {
    }

    public record PostingSummary(
            UUID id,
            UUID accountId,
            PostingDirection direction,
            long amountMinor,
            String currencyCode,
            OffsetDateTime postedAt,
            OffsetDateTime createdAt
    ) {
    }

    public record TransferSummary(
            UUID id,
            UUID sourceAccountId,
            UUID destinationAccountId,
            TransferStatus status,
            String externalReference,
            long amountMinor,
            String currencyCode,
            OffsetDateTime requestedAt,
            OffsetDateTime completedAt
    ) {
    }

    public record ReversalSummary(
            UUID id,
            UUID originalTransferId,
            UUID originalLedgerTransactionId,
            UUID reversalLedgerTransactionId,
            ReversalStatus status,
            ReversalReasonCode reasonCode,
            String correlationId,
            OffsetDateTime requestedAt,
            OffsetDateTime completedAt
    ) {
    }

    public record AdjustmentSummary(
            UUID id,
            UUID ledgerTransactionId,
            AdjustmentStatus status,
            AdjustmentReasonCode reasonCode,
            String correlationId,
            OffsetDateTime requestedAt,
            OffsetDateTime completedAt
    ) {
    }

    public record OutboxEventSummary(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            OutboxStatus status,
            String correlationId,
            int retryCount,
            OffsetDateTime createdAt,
            OffsetDateTime publishedAt
    ) {
    }
}
