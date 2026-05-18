package dev.kavrin.banking_ledger.ledger.persistence.mapper;

import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.ledger.domain.factory.PostedLedgerGraph;
import dev.kavrin.banking_ledger.ledger.domain.model.Posting;
import dev.kavrin.banking_ledger.ledger.domain.model.PostingDirection;
import dev.kavrin.banking_ledger.ledger.persistence.entity.JournalEntryEntity;
import dev.kavrin.banking_ledger.ledger.persistence.entity.LedgerTransactionEntity;
import dev.kavrin.banking_ledger.ledger.persistence.entity.PostingEntity;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class LedgerPersistenceMapper {

    public LedgerTransactionEntity toLedgerTransactionEntity(
            PostedLedgerGraph graph,
            OffsetDateTime postedAt
    ) {
        var transaction = graph.ledgerTransaction().posted();

        return LedgerTransactionEntity.builder()
                .externalReference(transaction.externalReference())
                .transactionType(transaction.transactionType())
                .currencyCode(transaction.currencyCode())
                .amountMinor(transaction.amountMinor())
                .description(transaction.description())
                .status(transaction.status())
                .postedAt(postedAt)
                .createdAt(transaction.createdAt())
                .build();
    }

    public JournalEntryEntity toJournalEntryEntity(
            PostedLedgerGraph graph,
            LedgerTransactionEntity transactionEntity,
            OffsetDateTime postedAt
    ) {

        return JournalEntryEntity.builder()
                .ledgerTransaction(transactionEntity)
                .entryType(graph.ledgerTransaction().transactionType().toJournalEntryType())
                .currencyCode(graph.journalEntry().getCurrencyCode())
                .totalDebitMinor(total(graph.journalEntry().getPostings(), PostingDirection.DEBIT))
                .totalCreditMinor(total(graph.journalEntry().getPostings(), PostingDirection.CREDIT))
                .description(graph.ledgerTransaction().description())
                .postedAt(postedAt)
                .createdAt(graph.journalEntry().getCreatedAt())
                .build();
    }

    public List<PostingEntity> toPostingEntities(
            PostedLedgerGraph graph,
            JournalEntryEntity journalEntryEntity,
            Map<UUID, AccountEntity> accountsById,
            OffsetDateTime postedAt
    ) {
        return graph.journalEntry().getPostings()
                .stream()
                .map(posting -> toPostingEntity(posting, journalEntryEntity, accountsById.get(posting.accountId()), postedAt))
                .toList();
    }

    private PostingEntity toPostingEntity(
            Posting posting,
            JournalEntryEntity journalEntryEntity,
            AccountEntity account,
            OffsetDateTime postedAt
    ) {
        return PostingEntity.builder()
                .journalEntry(journalEntryEntity)
                .account(account)
                .direction(posting.direction())
                .amountMinor(posting.amountMinor())
                .currencyCode(posting.currencyCode())
                .postedAt(postedAt)
                .build();
    }

    private long total(List<Posting> postings, PostingDirection direction) {
        return postings.stream()
                .filter(posting -> posting.direction() == direction)
                .mapToLong(Posting::amountMinor)
                .sum();
    }
}
