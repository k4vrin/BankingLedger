package dev.kavrin.banking_ledger.ledger.application.service;

import dev.kavrin.banking_ledger.account.domain.model.AccountCategory;
import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.account.persistence.AccountRepository;
import dev.kavrin.banking_ledger.audit.application.command.WriteAuditEventCommand;
import dev.kavrin.banking_ledger.audit.application.service.AuditEventWriter;
import dev.kavrin.banking_ledger.audit.domain.model.AuditChannel;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEntityType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEventType;
import dev.kavrin.banking_ledger.audit.domain.model.LedgerTransactionPostedAuditPayload;
import dev.kavrin.banking_ledger.ledger.application.command.PostLedgerTransactionCommand;
import dev.kavrin.banking_ledger.ledger.domain.factory.JournalEntryFactory;
import dev.kavrin.banking_ledger.ledger.domain.factory.PostedLedgerGraph;
import dev.kavrin.banking_ledger.ledger.persistence.entity.PostingEntity;
import dev.kavrin.banking_ledger.ledger.persistence.mapper.LedgerPersistenceMapper;
import dev.kavrin.banking_ledger.ledger.persistence.repository.JournalEntryRepository;
import dev.kavrin.banking_ledger.ledger.persistence.repository.LedgerTransactionRepository;
import dev.kavrin.banking_ledger.ledger.persistence.repository.PostingRepository;
import dev.kavrin.banking_ledger.outbox.application.command.WriteOutboxEventCommand;
import dev.kavrin.banking_ledger.outbox.application.service.OutboxWriterService;
import dev.kavrin.banking_ledger.outbox.domain.model.*;
import dev.kavrin.banking_ledger.shared.error.*;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostLedgerTransactionUseCase {

    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final PostingRepository postingRepository;
    private final AccountRepository accountRepository;
    private final AuditEventWriter auditEventWriter;
    private final OutboxWriterService outboxWriterService;
    private final LedgerPersistenceMapper mapper;
    private final AccountBalanceUpdater balanceUpdater;
    private final EntityManager entityManager;
    private final JournalEntryFactory journalEntryFactory = new JournalEntryFactory();

    @Transactional
    public PostedLedgerTransactionResult handle(PostLedgerTransactionCommand command) {
        return post(command, this::loadAndValidateAccounts);
    }

    @Transactional
    public PostedLedgerTransactionResult handleWithPreloadedAccounts(
            PostLedgerTransactionCommand command,
            PreloadedPostingAccounts preloadedAccounts
    ) {
        return post(command, graph -> validatePreloadedAccounts(graph, preloadedAccounts));
    }

    private PostedLedgerTransactionResult post(
            PostLedgerTransactionCommand command,
            PostingAccountResolver accountResolver
    ) {
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

        var accountsById = accountResolver.resolve(graph);
        var postedAt = OffsetDateTime.now();
        var transaction = mapper.toLedgerTransactionEntity(graph, postedAt);
        var savedTransaction = ledgerTransactionRepository.save(transaction);
        var journalEntry = mapper.toJournalEntryEntity(graph, savedTransaction, postedAt);
        var savedJournalEntry = journalEntryRepository.save(journalEntry);
        var postings = mapper.toPostingEntities(graph, savedJournalEntry, accountsById, postedAt);
        var savedPostings = postingRepository.saveAll(postings);

        var changedCustomerAccountIds = new LinkedHashSet<UUID>();
        for (var posting : graph.journalEntry().getPostings()) {
            var account = accountsById.get(posting.accountId());
            balanceUpdater.apply(account, posting);
            if (account.getAccountCategory() == AccountCategory.CUSTOMER) {
                changedCustomerAccountIds.add(account.getId());
            }
        }

        postingRepository.flush();
        writeAuditEvent(command, savedTransaction.getId());
        writeOutboxEvent(command, savedTransaction.getId(), postedAt);
        writeAccountBalanceChangedEvents(command, accountsById, changedCustomerAccountIds, postedAt);

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

    private Map<UUID, AccountEntity> validatePreloadedAccounts(
            PostedLedgerGraph graph,
            PreloadedPostingAccounts preloadedAccounts
    ) {
        var accountsById = preloadedAccounts.accountsById();
        accountsById.values().forEach(this::validateManagedPreloadedAccount);

        for (var posting : graph.journalEntry().getPostings()) {
            var account = accountsById.get(posting.accountId());
            if (account == null) {
                throw new ResourceNotFoundException(
                        ApiErrorCode.Business.POSTING_ACCOUNT_NOT_FOUND,
                        "Posting account not found in preloaded account set: " + posting.accountId(),
                        "Posting account not found."
                );
            }

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

    private void validateManagedPreloadedAccount(AccountEntity account) {
        if (!entityManager.contains(account)) {
            throw new IllegalArgumentException("Preloaded posting account must be managed in the active transaction.");
        }
    }

    private interface PostingAccountResolver {
        Map<UUID, AccountEntity> resolve(PostedLedgerGraph graph);
    }

    private void writeAuditEvent(PostLedgerTransactionCommand command, UUID transactionId) {
        auditEventWriter.write(new WriteAuditEventCommand(
                AuditEventType.LEDGER_TRANSACTION_POSTED,
                AuditEntityType.LEDGER_TRANSACTION,
                transactionId,
                command.actorType(),
                command.actorRole(),
                command.actorId(),
                command.correlationId(),
                AuditChannel.API,
                new LedgerTransactionPostedAuditPayload(
                        transactionId,
                        command.externalReference()
                )
        ));
    }

    private void writeOutboxEvent(
            PostLedgerTransactionCommand command,
            UUID transactionId,
            OffsetDateTime postedAt
    ) {
        var currencyCode = CurrencyCode.of(command.currencyCode()).value();
        outboxWriterService.write(new WriteOutboxEventCommand(
                OutboxAggregateType.LEDGER_TRANSACTION.name(),
                transactionId,
                OutboxEventType.LEDGER_TRANSACTION_POSTED,
                OutboxDestination.LEDGER_EVENTS,
                command.correlationId(),
                new LedgerTransactionPostedPayload(
                        transactionId,
                        command.externalReference(),
                        currencyCode,
                        command.amountMinor(),
                        postedAt
                )
        ));
    }

    private void writeAccountBalanceChangedEvents(
            PostLedgerTransactionCommand command,
            Map<UUID, AccountEntity> accountsById,
            LinkedHashSet<UUID> changedCustomerAccountIds,
            OffsetDateTime changedAt
    ) {
        for (UUID accountId : changedCustomerAccountIds) {
            var account = accountsById.get(accountId);
            outboxWriterService.write(new WriteOutboxEventCommand(
                    OutboxAggregateType.ACCOUNT.name(),
                    account.getId(),
                    OutboxEventType.ACCOUNT_BALANCE_CHANGED,
                    OutboxDestination.ACCOUNT_EVENTS,
                    command.correlationId(),
                    new AccountBalanceChangedPayload(
                            account.getId(),
                            account.getCurrencyCode(),
                            account.getLedgerBalanceMinor(),
                            account.getAvailableBalanceMinor(),
                            changedAt
                    )
            ));
        }
    }
}
