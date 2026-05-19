package dev.kavrin.banking_ledger.transfer.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.account.persistence.AccountRepository;
import dev.kavrin.banking_ledger.idempotency.application.service.IdempotencyService;
import dev.kavrin.banking_ledger.idempotency.application.service.TransferRequestHasher;
import dev.kavrin.banking_ledger.ledger.application.command.PostLedgerTransactionCommand;
import dev.kavrin.banking_ledger.ledger.application.command.PostingLineCommand;
import dev.kavrin.banking_ledger.ledger.application.service.PostLedgerTransactionUseCase;
import dev.kavrin.banking_ledger.ledger.domain.model.PostingDirection;
import dev.kavrin.banking_ledger.ledger.persistence.repository.LedgerTransactionRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.ConflictException;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;
import dev.kavrin.banking_ledger.transfer.application.command.CreateTransferCommand;
import dev.kavrin.banking_ledger.transfer.domain.model.TransferStatus;
import dev.kavrin.banking_ledger.transfer.domain.model.TransferType;
import dev.kavrin.banking_ledger.transfer.domain.policy.TransferValidationPolicy;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestEntity;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreateTransferUseCase {

    private final TransferRequestRepository transferRequestRepository;
    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final PostLedgerTransactionUseCase postLedgerTransactionUseCase;
    private final IdempotencyService idempotencyService;
    private final TransferRequestHasher transferRequestHasher;
    private final TransferResponseMapper transferResponseMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public CreateTransferResult handle(CreateTransferCommand command) {
        TransferValidationPolicy.validateRequest(
                command.sourceAccountId(),
                command.destinationAccountId(),
                command.amountMinor()
        );

        var requestHash = transferRequestHasher.hash(command);
        var existingIdempotencyRecord = idempotencyService.findTransferCreate(command.idempotencyKey());
        if (existingIdempotencyRecord.isPresent()) {
            var record = existingIdempotencyRecord.get();
            idempotencyService.rejectIfHashMismatch(record, requestHash);
            return new CreateTransferResult(
                    HttpStatus.OK.value(),
                    record.getResponseBody(),
                    record.getResourceId(),
                    true
            );
        }

        var source = accountRepository.findById(command.sourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ApiErrorCode.Business.ACCOUNT_NOT_FOUND,
                        "Source account not found: " + command.sourceAccountId(),
                        "Source account not found."
                ));
        var destination = accountRepository.findById(command.destinationAccountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ApiErrorCode.Business.ACCOUNT_NOT_FOUND,
                        "Destination account not found: " + command.destinationAccountId(),
                        "Destination account not found."
                ));

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

        var postedLedgerTransaction = postLedgerTransactionUseCase.handle(new PostLedgerTransactionCommand(
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
        ));

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
