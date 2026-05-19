package dev.kavrin.banking_ledger.transfer.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.account.application.service.LockedAccountLoader;
import dev.kavrin.banking_ledger.account.domain.model.LockedTransferAccounts;
import dev.kavrin.banking_ledger.idempotency.application.service.IdempotencyService;
import dev.kavrin.banking_ledger.idempotency.application.service.TransferRequestHasher;
import dev.kavrin.banking_ledger.ledger.application.command.PostLedgerTransactionCommand;
import dev.kavrin.banking_ledger.ledger.application.command.PostingLineCommand;
import dev.kavrin.banking_ledger.ledger.application.service.PostLedgerTransactionUseCase;
import dev.kavrin.banking_ledger.ledger.application.service.PreloadedPostingAccounts;
import dev.kavrin.banking_ledger.ledger.domain.model.PostingDirection;
import dev.kavrin.banking_ledger.ledger.persistence.repository.LedgerTransactionRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.ConflictException;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;
import dev.kavrin.banking_ledger.transfer.application.command.CreateTransferCommand;
import dev.kavrin.banking_ledger.transfer.domain.model.TransferStatus;
import dev.kavrin.banking_ledger.transfer.domain.model.TransferType;
import dev.kavrin.banking_ledger.transfer.domain.policy.TransferValidationPolicy;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestEntity;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreateTransferUseCase {

    private final TransferRequestRepository transferRequestRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final PostLedgerTransactionUseCase postLedgerTransactionUseCase;
    private final IdempotencyService idempotencyService;
    private final TransferRequestHasher transferRequestHasher;
    private final TransferResponseMapper transferResponseMapper;
    private final LockedAccountLoader lockedAccountLoader;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    // Uses Spring's default Propagation.REQUIRED so all transfer,
    // ledger, and idempotency writes commit atomically in one transaction.
    public CreateTransferResult handle(CreateTransferCommand command) {
        try {
            return transactionTemplate.execute(status -> handleInTransaction(command));
        } catch (DataIntegrityViolationException exception) {
            return handleDuplicateIdempotencyRace(command, exception);
        }
    }

    private CreateTransferResult handleInTransaction(CreateTransferCommand command) {
        TransferValidationPolicy.validateRequest(
                command.sourceAccountId(),
                command.destinationAccountId(),
                command.amountMinor()
        );

        var requestHash = transferRequestHasher.hash(command);
        var existingIdempotencyRecord = idempotencyService.findTransferCreate(command.idempotencyKey());
        if (existingIdempotencyRecord.isPresent()) {
            return replayExistingIdempotencyRecord(requestHash, existingIdempotencyRecord.get());
        }

        // Transfers use pessimistic row locking. If a row lock cannot be acquired,
        // the exception is mapped to a retryable 409 response by GlobalExceptionHandler.
        // Do not retry inside this @Transactional method; retrying here would reuse
        // a transaction that may already be marked rollback-only.
        LockedTransferAccounts lockedAccounts =
                lockedAccountLoader.loadForTransfer(
                        command.sourceAccountId(),
                        command.destinationAccountId()
                );

        var source = lockedAccounts.sourceAccount();
        var destination = lockedAccounts.destinationAccount();

        existingIdempotencyRecord = idempotencyService.findTransferCreate(command.idempotencyKey());
        if (existingIdempotencyRecord.isPresent()) {
            return replayExistingIdempotencyRecord(requestHash, existingIdempotencyRecord.get());
        }

        var currencyCode = CurrencyCode.of(command.currencyCode().value());
        validateDuplicateExternalReference(command);
        TransferValidationPolicy.validateAccounts(source, destination, currencyCode, command.amountMinor());

        var transfer = transferRequestRepository.save(TransferRequestEntity.builder()
                .sourceAccount(source)
                .destinationAccount(destination)
                .externalReference(normalizeNullable(command.externalReference()))
                .transferType(TransferType.INTERNAL)
                .requestedByActorType(command.actorType())
                .status(TransferStatus.PENDING)
                .currencyCode(currencyCode.value())
                .amountMinor(command.amountMinor())
                .description(normalizeNullable(command.description()))
                .requestedAt(OffsetDateTime.now())
                .build());

        var postedLedgerTransaction = postLedgerTransactionUseCase.handleWithPreloadedAccounts(new PostLedgerTransactionCommand(
                transfer.getExternalReference(),
                "TRANSFER",
                transfer.getCurrencyCode(),
                transfer.getAmountMinor(),
                transfer.getDescription(),
                command.actorType().name(),
                command.correlationId(),
                List.of(
                        new PostingLineCommand(source.getId(), PostingDirection.DEBIT, transfer.getAmountMinor(), transfer.getCurrencyCode()),
                        new PostingLineCommand(destination.getId(), PostingDirection.CREDIT, transfer.getAmountMinor(), transfer.getCurrencyCode())
                )
        ), PreloadedPostingAccounts.from(source, destination));

        transfer.setLedgerTransaction(ledgerTransactionRepository.getReferenceById(postedLedgerTransaction.ledgerTransactionId()));
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setCompletedAt(postedLedgerTransaction.postedAt());
        var completedTransfer = transferRequestRepository.save(transfer);
        transferRequestRepository.flush();

        var responseBody = toJson(transferResponseMapper.toResponse(completedTransfer));
        idempotencyService.createTransferCreateRecord(
                command.idempotencyKey(),
                requestHash,
                responseBody,
                HttpStatus.CREATED.value(),
                completedTransfer.getId()
        );

        return new CreateTransferResult(
                HttpStatus.CREATED.value(),
                responseBody,
                completedTransfer.getId(),
                false
        );
    }

    private CreateTransferResult handleDuplicateIdempotencyRace(
            CreateTransferCommand command,
            DataIntegrityViolationException exception
    ) {
        var requestHash = transferRequestHasher.hash(command);

        var existingRecord = idempotencyService.findTransferCreate(command.idempotencyKey())
                .orElseThrow(() -> exception);

        idempotencyService.rejectIfHashMismatch(existingRecord, requestHash);

        return new CreateTransferResult(
                HttpStatus.OK.value(),
                existingRecord.getResponseBody(),
                existingRecord.getResourceId(),
                true
        );
    }

    private CreateTransferResult replayExistingIdempotencyRecord(
            String requestHash,
            dev.kavrin.banking_ledger.idempotency.persistence.entity.IdempotencyRecordEntity record
    ) {
        idempotencyService.rejectIfHashMismatch(record, requestHash);
        return new CreateTransferResult(
                HttpStatus.OK.value(),
                record.getResponseBody(),
                record.getResourceId(),
                true
        );
    }

    private void validateDuplicateExternalReference(CreateTransferCommand command) {
        if (command.externalReference() != null
                && !command.externalReference().isBlank()
                && transferRequestRepository.existsByExternalReference(command.externalReference().trim())) {
            throw new ConflictException(
                    ApiErrorCode.Business.DUPLICATE_REQUEST,
                    "Transfer external reference already exists: " + command.externalReference(),
                    "Transfer external reference already exists."
            );
        }
    }

    private String toJson(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize transfer response", exception);
        }
    }

    private String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }
}
