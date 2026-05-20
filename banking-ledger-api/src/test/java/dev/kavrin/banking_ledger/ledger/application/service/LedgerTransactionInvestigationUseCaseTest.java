package dev.kavrin.banking_ledger.ledger.application.service;

import dev.kavrin.banking_ledger.account.domain.model.AccountCategory;
import dev.kavrin.banking_ledger.account.domain.model.AccountStatus;
import dev.kavrin.banking_ledger.account.domain.model.AccountType;
import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.adjustment.persistence.AdjustmentRequestRepository;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEntityType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEventType;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventEntity;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventRepository;
import dev.kavrin.banking_ledger.ledger.domain.model.JournalEntryType;
import dev.kavrin.banking_ledger.ledger.domain.model.LedgerTransactionType;
import dev.kavrin.banking_ledger.ledger.domain.model.PostingDirection;
import dev.kavrin.banking_ledger.ledger.domain.model.TransactionStatus;
import dev.kavrin.banking_ledger.ledger.persistence.entity.JournalEntryEntity;
import dev.kavrin.banking_ledger.ledger.persistence.entity.LedgerTransactionEntity;
import dev.kavrin.banking_ledger.ledger.persistence.entity.PostingEntity;
import dev.kavrin.banking_ledger.ledger.persistence.repository.JournalEntryRepository;
import dev.kavrin.banking_ledger.ledger.persistence.repository.LedgerTransactionRepository;
import dev.kavrin.banking_ledger.ledger.persistence.repository.PostingRepository;
import dev.kavrin.banking_ledger.outbox.OutboxStatus;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventEntity;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import dev.kavrin.banking_ledger.reversal.persistence.ReversalRepository;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerTransactionInvestigationUseCaseTest {

    @Mock
    private LedgerTransactionRepository ledgerTransactionRepository;
    @Mock
    private JournalEntryRepository journalEntryRepository;
    @Mock
    private PostingRepository postingRepository;
    @Mock
    private TransferRequestRepository transferRequestRepository;
    @Mock
    private ReversalRepository reversalRepository;
    @Mock
    private AdjustmentRequestRepository adjustmentRequestRepository;
    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Test
    void getByTransactionIdAggregatesLedgerAuditAndOutboxData() {
        var now = OffsetDateTime.now();
        var transactionId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        var journalEntryId = UUID.randomUUID();
        var postingId = UUID.randomUUID();
        var auditEventId = UUID.randomUUID();
        var outboxEventId = UUID.randomUUID();
        var transaction = LedgerTransactionEntity.builder()
                .id(transactionId)
                .externalReference("ext-1")
                .transactionType(LedgerTransactionType.TRANSFER)
                .status(TransactionStatus.POSTED)
                .currencyCode("USD")
                .amountMinor(100L)
                .description("transfer")
                .postedAt(now)
                .createdAt(now)
                .build();
        var journalEntry = JournalEntryEntity.builder()
                .id(journalEntryId)
                .ledgerTransaction(transaction)
                .entryType(JournalEntryType.TRANSFER)
                .currencyCode("USD")
                .totalDebitMinor(100L)
                .totalCreditMinor(100L)
                .description("transfer")
                .postedAt(now)
                .build();
        var account = AccountEntity.builder()
                .id(accountId)
                .accountNumber("A-1")
                .accountType(AccountType.CURRENT)
                .accountCategory(AccountCategory.CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .currencyCode("USD")
                .build();
        var posting = PostingEntity.builder()
                .id(postingId)
                .journalEntry(journalEntry)
                .account(account)
                .direction(PostingDirection.DEBIT)
                .currencyCode("USD")
                .amountMinor(100L)
                .postedAt(now)
                .createdAt(now)
                .build();
        var auditEvent = AuditEventEntity.builder()
                .id(auditEventId)
                .eventType(AuditEventType.LEDGER_TRANSACTION_POSTED.name())
                .entityType(AuditEntityType.LEDGER_TRANSACTION.name())
                .entityId(transactionId)
                .actorType(AuditActorType.SYSTEM)
                .actorRole(AuditActorRole.SYSTEM)
                .actorId("system")
                .channel("SYSTEM")
                .correlationId("corr-1")
                .eventPayload("{}")
                .createdAt(now)
                .build();
        var outboxEvent = OutboxEventEntity.builder()
                .id(outboxEventId)
                .aggregateType("LEDGER_TRANSACTION")
                .aggregateId(transactionId)
                .eventType("LedgerTransactionPosted")
                .status(OutboxStatus.PENDING)
                .correlationId("corr-1")
                .retryCount(0)
                .eventPayload("{}")
                .createdAt(now)
                .build();

        when(ledgerTransactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(journalEntryRepository.findByLedgerTransaction_Id(transactionId)).thenReturn(Optional.of(journalEntry));
        when(postingRepository.findByJournalEntry_LedgerTransaction_Id(transactionId)).thenReturn(List.of(posting));
        when(transferRequestRepository.findByLedgerTransaction_Id(transactionId)).thenReturn(Optional.empty());
        when(reversalRepository.findByOriginalLedgerTransaction_Id(transactionId)).thenReturn(Optional.empty());
        when(reversalRepository.findByReversalLedgerTransaction_Id(transactionId)).thenReturn(Optional.empty());
        when(adjustmentRequestRepository.findByLedgerTransaction_Id(transactionId)).thenReturn(Optional.empty());
        when(auditEventRepository.findAll(any(Specification.class))).thenReturn(List.of(auditEvent));
        when(outboxEventRepository.findByAggregateIdIn(any())).thenReturn(List.of(outboxEvent));
        var useCase = useCase();

        var response = useCase.getByTransactionId(transactionId);

        assertThat(response.transaction().id()).isEqualTo(transactionId);
        assertThat(response.journalEntry().id()).isEqualTo(journalEntryId);
        assertThat(response.postings()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(postingId);
            assertThat(summary.accountId()).isEqualTo(accountId);
        });
        assertThat(response.auditEventIds()).containsExactly(auditEventId);
        assertThat(response.outboxEvents()).singleElement()
                .satisfies(summary -> assertThat(summary.id()).isEqualTo(outboxEventId));
    }

    @Test
    void getByTransactionIdReturnsNotFoundForMissingTransaction() {
        var transactionId = UUID.randomUUID();
        when(ledgerTransactionRepository.findById(transactionId)).thenReturn(Optional.empty());
        var useCase = useCase();

        assertThatThrownBy(() -> useCase.getByTransactionId(transactionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Ledger transaction not found");
    }

    private LedgerTransactionInvestigationUseCase useCase() {
        return new LedgerTransactionInvestigationUseCase(
                ledgerTransactionRepository,
                journalEntryRepository,
                postingRepository,
                transferRequestRepository,
                reversalRepository,
                adjustmentRequestRepository,
                auditEventRepository,
                outboxEventRepository
        );
    }
}
