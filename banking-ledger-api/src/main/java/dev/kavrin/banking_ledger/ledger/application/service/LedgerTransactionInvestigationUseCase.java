package dev.kavrin.banking_ledger.ledger.application.service;

import dev.kavrin.banking_ledger.adjustment.persistence.AdjustmentRequestEntity;
import dev.kavrin.banking_ledger.adjustment.persistence.AdjustmentRequestRepository;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEntityType;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventRepository;
import dev.kavrin.banking_ledger.ledger.api.dto.LedgerTransactionInvestigationResponse;
import dev.kavrin.banking_ledger.ledger.persistence.entity.JournalEntryEntity;
import dev.kavrin.banking_ledger.ledger.persistence.entity.LedgerTransactionEntity;
import dev.kavrin.banking_ledger.ledger.persistence.entity.PostingEntity;
import dev.kavrin.banking_ledger.ledger.persistence.repository.JournalEntryRepository;
import dev.kavrin.banking_ledger.ledger.persistence.repository.LedgerTransactionRepository;
import dev.kavrin.banking_ledger.ledger.persistence.repository.PostingRepository;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventEntity;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import dev.kavrin.banking_ledger.reversal.persistence.ReversalEntity;
import dev.kavrin.banking_ledger.reversal.persistence.ReversalRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestEntity;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerTransactionInvestigationUseCase {

    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final PostingRepository postingRepository;
    private final TransferRequestRepository transferRequestRepository;
    private final ReversalRepository reversalRepository;
    private final AdjustmentRequestRepository adjustmentRequestRepository;
    private final AuditEventRepository auditEventRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional(readOnly = true)
    public LedgerTransactionInvestigationResponse getByTransactionId(UUID transactionId) {
        var transaction = ledgerTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ApiErrorCode.Business.RESOURCE_NOT_FOUND,
                        "Ledger transaction not found: " + transactionId,
                        "Ledger transaction not found."
                ));
        var journalEntry = journalEntryRepository.findByLedgerTransaction_Id(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ApiErrorCode.Business.RESOURCE_NOT_FOUND,
                        "Journal entry not found for ledger transaction: " + transactionId,
                        "Journal entry not found."
                ));
        var postings = postingRepository.findByJournalEntry_LedgerTransaction_Id(transactionId);
        var transfer = transferRequestRepository.findByLedgerTransaction_Id(transactionId).orElse(null);
        var reversal = reversalRepository.findByOriginalLedgerTransaction_Id(transactionId)
                .or(() -> reversalRepository.findByReversalLedgerTransaction_Id(transactionId))
                .orElse(null);
        var adjustment = adjustmentRequestRepository.findByLedgerTransaction_Id(transactionId).orElse(null);
        var relatedIds = relatedIds(transactionId, transfer, reversal, adjustment);
        var auditEventIds = auditEventRepository.findAll(auditSpecification(relatedIds))
                .stream()
                .map(event -> event.getId())
                .toList();
        var outboxEvents = outboxEventRepository.findByAggregateIdIn(relatedIds)
                .stream()
                .map(this::toOutboxSummary)
                .toList();

        return new LedgerTransactionInvestigationResponse(
                toTransactionSummary(transaction),
                toJournalEntrySummary(journalEntry),
                postings.stream().map(this::toPostingSummary).toList(),
                transfer == null ? null : toTransferSummary(transfer),
                reversal == null ? null : toReversalSummary(reversal),
                adjustment == null ? null : toAdjustmentSummary(adjustment),
                auditEventIds,
                outboxEvents
        );
    }

    private LinkedHashSet<UUID> relatedIds(
            UUID transactionId,
            TransferRequestEntity transfer,
            ReversalEntity reversal,
            AdjustmentRequestEntity adjustment
    ) {
        var ids = new LinkedHashSet<UUID>();
        ids.add(transactionId);
        if (transfer != null) {
            ids.add(transfer.getId());
        }
        if (reversal != null) {
            ids.add(reversal.getId());
            ids.add(reversal.getOriginalTransfer().getId());
            ids.add(reversal.getOriginalLedgerTransaction().getId());
            if (reversal.getReversalLedgerTransaction() != null) {
                ids.add(reversal.getReversalLedgerTransaction().getId());
            }
        }
        if (adjustment != null) {
            ids.add(adjustment.getId());
        }
        return ids;
    }

    private Specification<dev.kavrin.banking_ledger.audit.persistence.AuditEventEntity> auditSpecification(LinkedHashSet<UUID> relatedIds) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.and(
                        criteriaBuilder.equal(root.get("entityType"), AuditEntityType.LEDGER_TRANSACTION.name()),
                        root.get("entityId").in(relatedIds)
                ),
                root.get("entityId").in(relatedIds)
        );
    }

    private LedgerTransactionInvestigationResponse.LedgerTransactionSummary toTransactionSummary(
            LedgerTransactionEntity transaction
    ) {
        return new LedgerTransactionInvestigationResponse.LedgerTransactionSummary(
                transaction.getId(),
                transaction.getExternalReference(),
                transaction.getTransactionType(),
                transaction.getStatus(),
                transaction.getCurrencyCode(),
                transaction.getAmountMinor(),
                transaction.getDescription(),
                transaction.getPostedAt(),
                transaction.getCreatedAt()
        );
    }

    private LedgerTransactionInvestigationResponse.JournalEntrySummary toJournalEntrySummary(
            JournalEntryEntity journalEntry
    ) {
        return new LedgerTransactionInvestigationResponse.JournalEntrySummary(
                journalEntry.getId(),
                journalEntry.getEntryType(),
                journalEntry.getCurrencyCode(),
                journalEntry.getTotalDebitMinor(),
                journalEntry.getTotalCreditMinor(),
                journalEntry.getDescription(),
                journalEntry.getPostedAt()
        );
    }

    private LedgerTransactionInvestigationResponse.PostingSummary toPostingSummary(PostingEntity posting) {
        return new LedgerTransactionInvestigationResponse.PostingSummary(
                posting.getId(),
                posting.getAccount().getId(),
                posting.getDirection(),
                posting.getAmountMinor(),
                posting.getCurrencyCode(),
                posting.getPostedAt(),
                posting.getCreatedAt()
        );
    }

    private LedgerTransactionInvestigationResponse.TransferSummary toTransferSummary(TransferRequestEntity transfer) {
        return new LedgerTransactionInvestigationResponse.TransferSummary(
                transfer.getId(),
                transfer.getSourceAccount().getId(),
                transfer.getDestinationAccount().getId(),
                transfer.getStatus(),
                transfer.getExternalReference(),
                transfer.getAmountMinor(),
                transfer.getCurrencyCode(),
                transfer.getRequestedAt(),
                transfer.getCompletedAt()
        );
    }

    private LedgerTransactionInvestigationResponse.ReversalSummary toReversalSummary(ReversalEntity reversal) {
        return new LedgerTransactionInvestigationResponse.ReversalSummary(
                reversal.getId(),
                reversal.getOriginalTransfer().getId(),
                reversal.getOriginalLedgerTransaction().getId(),
                reversal.getReversalLedgerTransaction() == null ? null : reversal.getReversalLedgerTransaction().getId(),
                reversal.getStatus(),
                reversal.getReasonCode(),
                reversal.getCorrelationId(),
                reversal.getRequestedAt(),
                reversal.getCompletedAt()
        );
    }

    private LedgerTransactionInvestigationResponse.AdjustmentSummary toAdjustmentSummary(AdjustmentRequestEntity adjustment) {
        return new LedgerTransactionInvestigationResponse.AdjustmentSummary(
                adjustment.getId(),
                adjustment.getLedgerTransaction() == null ? null : adjustment.getLedgerTransaction().getId(),
                adjustment.getStatus(),
                adjustment.getReasonCode(),
                adjustment.getCorrelationId(),
                adjustment.getRequestedAt(),
                adjustment.getCompletedAt()
        );
    }

    private LedgerTransactionInvestigationResponse.OutboxEventSummary toOutboxSummary(OutboxEventEntity outboxEvent) {
        return new LedgerTransactionInvestigationResponse.OutboxEventSummary(
                outboxEvent.getId(),
                outboxEvent.getAggregateType(),
                outboxEvent.getAggregateId(),
                outboxEvent.getEventType(),
                outboxEvent.getStatus(),
                outboxEvent.getCorrelationId(),
                outboxEvent.getRetryCount(),
                outboxEvent.getCreatedAt(),
                outboxEvent.getPublishedAt()
        );
    }
}
