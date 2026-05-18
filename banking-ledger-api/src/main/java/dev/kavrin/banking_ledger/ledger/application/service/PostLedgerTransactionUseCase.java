package dev.kavrin.banking_ledger.ledger.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.account.persistence.AccountRepository;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventEntity;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventRepository;
import dev.kavrin.banking_ledger.ledger.application.command.PostLedgerTransactionCommand;
import dev.kavrin.banking_ledger.ledger.domain.factory.JournalEntryFactory;
import dev.kavrin.banking_ledger.ledger.domain.factory.PostedLedgerGraph;
import dev.kavrin.banking_ledger.ledger.domain.model.LedgerTransactionType;
import dev.kavrin.banking_ledger.ledger.persistence.entity.PostingEntity;
import dev.kavrin.banking_ledger.ledger.persistence.mapper.LedgerPersistenceMapper;
import dev.kavrin.banking_ledger.ledger.persistence.repository.JournalEntryRepository;
import dev.kavrin.banking_ledger.ledger.persistence.repository.LedgerTransactionRepository;
import dev.kavrin.banking_ledger.ledger.persistence.repository.PostingRepository;
import dev.kavrin.banking_ledger.outbox.OutboxStatus;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventEntity;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import dev.kavrin.banking_ledger.shared.error.BusinessRuleViolationException;
import dev.kavrin.banking_ledger.shared.error.ConflictException;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostLedgerTransactionUseCase {

    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final PostingRepository postingRepository;
    private final AccountRepository accountRepository;
    private final AuditEventRepository auditEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final LedgerPersistenceMapper mapper;
    private final AccountBalanceUpdater balanceUpdater;
    private final ObjectMapper objectMapper;
    private final JournalEntryFactory journalEntryFactory = new JournalEntryFactory();

    @Transactional
    public PostedLedgerTransactionResult handle(PostLedgerTransactionCommand command) {
        validateTransactionType(command.transactionType());
        var graph = createGraph(command);

        if (graph.ledgerTransaction().externalReference() != null
                && !graph.ledgerTransaction().externalReference().isBlank()
                && ledgerTransactionRepository.existsByExternalReference(graph.ledgerTransaction().externalReference())) {
            throw new ConflictException(
                    ApiErrorCode.Business.LEDGER_TRANSACTION_ALREADY_EXISTS,
                    "Ledger transaction external reference already exists: " + graph.ledgerTransaction().externalReference(),
                    "Ledger transaction already exists."
            );
        }

        var accountsById = loadAndValidateAccounts(graph);
        var postedAt = OffsetDateTime.now();
        var transaction = mapper.toLedgerTransactionEntity(graph, postedAt);
        var savedTransaction = ledgerTransactionRepository.save(transaction);
        var journalEntry = mapper.toJournalEntryEntity(graph, savedTransaction, postedAt);
        var savedJournalEntry = journalEntryRepository.save(journalEntry);
        var postings = mapper.toPostingEntities(graph, savedJournalEntry, accountsById, postedAt);
        var savedPostings = postingRepository.saveAll(postings);

        for (var posting : graph.journalEntry().getPostings()) {
            balanceUpdater.apply(accountsById.get(posting.accountId()), posting);
        }

        postingRepository.flush();
        auditEventRepository.save(auditEvent(command, savedTransaction.getId()));
        outboxEventRepository.save(outboxEvent(command, savedTransaction.getId(), postedAt));

        return new PostedLedgerTransactionResult(
                savedTransaction.getId(),
                savedJournalEntry.getId(),
                savedPostings.stream().map(PostingEntity::getId).toList(),
                postedAt
        );
    }

    private PostedLedgerGraph createGraph(PostLedgerTransactionCommand command) {
        try {
            return journalEntryFactory.create(command);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    ApiErrorCode.Validation.INVALID_REQUEST,
                    exception.getMessage(),
                    "Ledger transaction request is invalid."
            );
        }
    }

    private Map<UUID, AccountEntity> loadAndValidateAccounts(PostedLedgerGraph graph) {
        Map<UUID, AccountEntity> accountsById = new HashMap<>();

        for (var posting : graph.journalEntry().getPostings()) {
            var account = accountsById.computeIfAbsent(posting.accountId(), accountId ->
                    accountRepository.findById(accountId)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    ApiErrorCode.Business.POSTING_ACCOUNT_NOT_FOUND,
                                    "Posting account not found: " + accountId,
                                    "Posting account not found."
                            )));

            if (!account.getCurrencyCode().equals(posting.currencyCode())) {
                throw new BusinessRuleViolationException(
                        ApiErrorCode.Business.POSTING_ACCOUNT_CURRENCY_MISMATCH,
                        "Posting currency " + posting.currencyCode()
                                + " does not match account currency " + account.getCurrencyCode(),
                        "Posting currency must match account currency."
                );
            }
        }

        return accountsById;
    }

    private AuditEventEntity auditEvent(PostLedgerTransactionCommand command, UUID transactionId) {
        return AuditEventEntity.builder()
                .eventType("LEDGER_TRANSACTION_POSTED")
                .entityType("LEDGER_TRANSACTION")
                .entityId(transactionId)
                .actorType(parseActorType(command.actorType()))
                .correlationId(command.correlationId())
                .eventPayload(toJson(Map.of(
                        "transactionId", transactionId.toString(),
                        "externalReference", command.externalReference() == null ? "" : command.externalReference()
                )))
                .build();
    }

    private OutboxEventEntity outboxEvent(
            PostLedgerTransactionCommand command,
            UUID transactionId,
            OffsetDateTime postedAt
    ) {
        var currencyCode = CurrencyCode.of(command.currencyCode()).value();
        return OutboxEventEntity.builder()
                .aggregateType("LEDGER_TRANSACTION")
                .aggregateId(transactionId)
                .eventType("LedgerTransactionPosted")
                .destination("ledger-events")
                .correlationId(command.correlationId())
                .eventPayload(toJson(Map.of(
                        "transactionId", transactionId.toString(),
                        "currencyCode", currencyCode,
                        "amountMinor", command.amountMinor(),
                        "transactionType", command.transactionType(),
                        "postedAt", postedAt.toString()
                )))
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();
    }

    private AuditActorType parseActorType(String actorType) {
        try {
            return AuditActorType.valueOf(actorType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    ApiErrorCode.Validation.INVALID_REQUEST,
                    "Invalid actor type: " + actorType,
                    "Invalid actor type."
            );
        }
    }

    private void validateTransactionType(String transactionType) {
        try {
            LedgerTransactionType.valueOf(transactionType);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    ApiErrorCode.Validation.INVALID_REQUEST,
                    "Invalid transaction type: " + transactionType,
                    "Invalid transaction type."
            );
        }
    }

    private String toJson(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize event payload", exception);
        }
    }
}
