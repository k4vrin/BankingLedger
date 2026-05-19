package dev.kavrin.banking_ledger.reversal.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.audit.application.service.AuditEventWriter;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEntityType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEventType;
import dev.kavrin.banking_ledger.ledger.application.command.PostLedgerTransactionCommand;
import dev.kavrin.banking_ledger.ledger.application.command.PostingLineCommand;
import dev.kavrin.banking_ledger.ledger.application.service.PostLedgerTransactionUseCase;
import dev.kavrin.banking_ledger.ledger.domain.model.LedgerTransactionType;
import dev.kavrin.banking_ledger.ledger.domain.model.PostingDirection;
import dev.kavrin.banking_ledger.ledger.persistence.entity.LedgerTransactionEntity;
import dev.kavrin.banking_ledger.ledger.persistence.entity.PostingEntity;
import dev.kavrin.banking_ledger.ledger.persistence.repository.LedgerTransactionRepository;
import dev.kavrin.banking_ledger.ledger.persistence.repository.PostingRepository;
import dev.kavrin.banking_ledger.outbox.OutboxAggregateType;
import dev.kavrin.banking_ledger.outbox.OutboxDestination;
import dev.kavrin.banking_ledger.outbox.OutboxEventType;
import dev.kavrin.banking_ledger.outbox.OutboxStatus;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventEntity;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import dev.kavrin.banking_ledger.reversal.api.dto.ReversalResponse;
import dev.kavrin.banking_ledger.reversal.application.command.ReverseTransferCommand;
import dev.kavrin.banking_ledger.reversal.domain.model.ReversalStatus;
import dev.kavrin.banking_ledger.reversal.domain.policy.ReversalValidationPolicy;
import dev.kavrin.banking_ledger.reversal.persistence.ReversalEntity;
import dev.kavrin.banking_ledger.reversal.persistence.ReversalRepository;
import dev.kavrin.banking_ledger.reversal.persistence.mapper.ReversalPersistenceMapper;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import dev.kavrin.banking_ledger.transfer.domain.model.TransferStatus;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestEntity;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReverseTransferUseCase {

    private final TransferRequestRepository transferRequestRepository;
    private final ReversalRepository reversalRepository;
    private final PostLedgerTransactionUseCase postLedgerTransactionUseCase;
    private final ReversalPersistenceMapper reversalPersistenceMapper;
    private final PostingRepository postingRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final AuditEventWriter auditEventWriter;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ReversalResponse handle(ReverseTransferCommand command) {
        ReversalValidationPolicy.validateRequest(command);

        TransferRequestEntity transfer = transferRequestRepository.findByIdForUpdate(command.transferId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ApiErrorCode.Business.RESOURCE_NOT_FOUND,
                        "Transfer not found: " + command.transferId(),
                        "Transfer not found."
                ));

        boolean duplicateExists =
                reversalRepository.existsByOriginalTransfer_Id(command.transferId());

        ReversalValidationPolicy.validateTransferCanBeReversed(
                transfer,
                duplicateExists
        );

        LedgerTransactionEntity originalLedgerTransaction =
                transfer.getLedgerTransaction();

        List<PostingEntity> originalPostings =
                postingRepository.findByJournalEntry_LedgerTransaction_Id(
                        originalLedgerTransaction.getId()
                );

        if (originalPostings.isEmpty()) {
            throw new ResourceNotFoundException(
                    ApiErrorCode.Business.POSTING_ACCOUNT_NOT_FOUND,
                    "Original ledger transaction has no postings: " + originalLedgerTransaction.getId(),
                    "Original ledger transaction has no postings."
            );
        }

        ReversalEntity reversal = reversalRepository.save(
                ReversalEntity.builder()
                        .originalTransfer(transfer)
                        .originalLedgerTransaction(originalLedgerTransaction)
                        .reasonCode(command.reasonCode())
                        .reasonDetail(command.reasonDetail())
                        .requestedByActorType(command.actorType())
                        .requestedByActorRole(command.actorRole())
                        .requestedByActorId(command.actorId())
                        .correlationId(command.correlationId())
                        .requestedAt(OffsetDateTime.now())
                        .status(ReversalStatus.PENDING)
                        .build()
        );

        PostLedgerTransactionCommand ledgerCommand =
                new PostLedgerTransactionCommand(
                        reversalExternalReference(transfer),
                        LedgerTransactionType.REVERSAL,
                        originalLedgerTransaction.getCurrencyCode(),
                        originalLedgerTransaction.getAmountMinor(),
                        reversalDescription(transfer, command.reasonDetail()),
                        command.actorType().toAuditActorType(),
                        command.correlationId(),
                        buildReversalPostingLines(originalPostings)
                );

        var postedReversalLedger =
                postLedgerTransactionUseCase.handle(ledgerCommand);

        LedgerTransactionEntity reversalLedgerReference =
                ledgerTransactionRepository.getReferenceById(postedReversalLedger.ledgerTransactionId());

        reversal.setReversalLedgerTransaction(reversalLedgerReference);
        reversal.setStatus(ReversalStatus.COMPLETED);
        reversal.setCompletedAt(postedReversalLedger.postedAt());

        transfer.setStatus(TransferStatus.REVERSED);

        ReversalEntity completedReversal = reversalRepository.save(reversal);
        transferRequestRepository.save(transfer);
        writeAuditEvent(command, completedReversal);
        outboxEventRepository.save(outboxEvent(command, completedReversal));

        return reversalPersistenceMapper.toResponse(completedReversal);
    }

    private List<PostingLineCommand> buildReversalPostingLines(
            List<PostingEntity> originalPostings
    ) {
        return originalPostings.stream()
                .map(posting -> new PostingLineCommand(
                        posting.getAccount().getId(),
                        opposite(posting.getDirection()),
                        posting.getAmountMinor(),
                        posting.getCurrencyCode()
                ))
                .toList();
    }

    private PostingDirection opposite(PostingDirection direction) {
        return switch (direction) {
            case DEBIT -> PostingDirection.CREDIT;
            case CREDIT -> PostingDirection.DEBIT;
        };
    }

    private String reversalExternalReference(TransferRequestEntity transfer) {
        return "REVERSAL-TRANSFER-" + transfer.getId();
    }

    private String reversalDescription(
            TransferRequestEntity transfer,
            String reasonDetail
    ) {
        if (reasonDetail == null || reasonDetail.isBlank()) {
            return "Reversal for transfer " + transfer.getId();
        }

        return "Reversal for transfer " + transfer.getId() + ": " + reasonDetail.trim();
    }

    private void writeAuditEvent(ReverseTransferCommand command, ReversalEntity reversal) {
        auditEventWriter.write(
                AuditEventType.TRANSFER_REVERSED,
                AuditEntityType.TRANSFER,
                reversal.getOriginalTransfer().getId(),
                command.actorType().toAuditActorType(),
                command.actorRole(),
                command.actorId(),
                command.correlationId(),
                "API",
                Map.of(
                        "reversalId", reversal.getId().toString(),
                        "originalTransferId", reversal.getOriginalTransfer().getId().toString(),
                        "originalLedgerTransactionId", reversal.getOriginalLedgerTransaction().getId().toString(),
                        "reversalLedgerTransactionId", reversal.getReversalLedgerTransaction().getId().toString(),
                        "reasonCode", command.reasonCode().name()
                )
        );
    }

    private OutboxEventEntity outboxEvent(ReverseTransferCommand command, ReversalEntity reversal) {
        return OutboxEventEntity.builder()
                .aggregateType(OutboxAggregateType.TRANSFER.name())
                .aggregateId(reversal.getOriginalTransfer().getId())
                .eventType(OutboxEventType.LEDGER_TRANSACTION_REVERSED.eventName())
                .destination(OutboxDestination.LEDGER_EVENTS.destinationName())
                .correlationId(command.correlationId())
                .eventPayload(toJson(Map.of(
                        "reversalId", reversal.getId().toString(),
                        "originalTransferId", reversal.getOriginalTransfer().getId().toString(),
                        "originalLedgerTransactionId", reversal.getOriginalLedgerTransaction().getId().toString(),
                        "reversalLedgerTransactionId", reversal.getReversalLedgerTransaction().getId().toString(),
                        "reasonCode", command.reasonCode().name(),
                        "completedAt", reversal.getCompletedAt().toString()
                )))
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();
    }

    private String toJson(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize event payload", exception);
        }
    }

}
