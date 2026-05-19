package dev.kavrin.banking_ledger.reversal.application.service;

import dev.kavrin.banking_ledger.ledger.application.command.PostLedgerTransactionCommand;
import dev.kavrin.banking_ledger.ledger.application.command.PostingLineCommand;
import dev.kavrin.banking_ledger.ledger.application.service.PostLedgerTransactionUseCase;
import dev.kavrin.banking_ledger.ledger.domain.model.PostingDirection;
import dev.kavrin.banking_ledger.ledger.persistence.entity.LedgerTransactionEntity;
import dev.kavrin.banking_ledger.ledger.persistence.entity.PostingEntity;
import dev.kavrin.banking_ledger.ledger.persistence.repository.PostingRepository;
import dev.kavrin.banking_ledger.reversal.api.dto.ReversalResponse;
import dev.kavrin.banking_ledger.reversal.application.command.ReverseTransferCommand;
import dev.kavrin.banking_ledger.reversal.domain.model.ReversalStatus;
import dev.kavrin.banking_ledger.reversal.domain.policy.ReversalValidationPolicy;
import dev.kavrin.banking_ledger.reversal.persistence.ReversalEntity;
import dev.kavrin.banking_ledger.reversal.persistence.ReversalRepository;
import dev.kavrin.banking_ledger.reversal.persistence.mapper.ReversalPersistenceMapper;
import dev.kavrin.banking_ledger.transfer.domain.model.TransferStatus;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestEntity;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReverseTransferUseCase {

    private final TransferRequestRepository transferRequestRepository;
    private final ReversalRepository reversalRepository;
    private final PostLedgerTransactionUseCase postLedgerTransactionUseCase;
    private final ReversalPersistenceMapper reversalPersistenceMapper;
    private final PostingRepository postingRepository;

    @Transactional
    public ReversalResponse handle(ReverseTransferCommand command) {
        ReversalValidationPolicy.validateRequest(command);

        TransferRequestEntity transfer = transferRequestRepository.findByIdForUpdate(command.transferId())
                .orElseThrow(() -> new IllegalArgumentException("transfer not found"));

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

            throw new IllegalStateException("Original ledger transaction has no postings");

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
                        "REVERSAL",
                        originalLedgerTransaction.getCurrencyCode(),
                        originalLedgerTransaction.getAmountMinor(),
                        reversalDescription(transfer, command.reasonDetail()),
                        command.actorType().name(),
                        command.correlationId(),
                        buildReversalPostingLines(originalPostings)
                );

        var postedReversalLedger =
                postLedgerTransactionUseCase.handle(ledgerCommand);

        LedgerTransactionEntity reversalLedgerReference =
                originalLedgerTransactionReference(postedReversalLedger.ledgerTransactionId());

        reversal.setReversalLedgerTransaction(reversalLedgerReference);
        reversal.setStatus(ReversalStatus.COMPLETED);
        reversal.setCompletedAt(postedReversalLedger.postedAt());

        transfer.setStatus(TransferStatus.REVERSED);

        ReversalEntity completedReversal = reversalRepository.save(reversal);
        transferRequestRepository.save(transfer);

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

    private LedgerTransactionEntity originalLedgerTransactionReference(
            java.util.UUID ledgerTransactionId
    ) {
        return LedgerTransactionEntity.builder()
                .id(ledgerTransactionId)
                .build();
    }
}
