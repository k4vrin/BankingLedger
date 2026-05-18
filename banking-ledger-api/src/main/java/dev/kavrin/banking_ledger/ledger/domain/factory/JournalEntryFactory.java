package dev.kavrin.banking_ledger.ledger.domain.factory;

import dev.kavrin.banking_ledger.ledger.application.command.PostLedgerTransactionCommand;
import dev.kavrin.banking_ledger.ledger.application.command.PostingLineCommand;
import dev.kavrin.banking_ledger.ledger.domain.model.JournalEntry;
import dev.kavrin.banking_ledger.ledger.domain.model.LedgerTransaction;
import dev.kavrin.banking_ledger.ledger.domain.model.LedgerTransactionType;
import dev.kavrin.banking_ledger.ledger.domain.model.Posting;
import dev.kavrin.banking_ledger.ledger.domain.policy.DoubleEntryPostingPolicy;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;

import java.util.UUID;

public class JournalEntryFactory {

    public PostedLedgerGraph create(PostLedgerTransactionCommand command) {
        var transactionCurrency = CurrencyCode.of(command.currencyCode());

        var transaction = LedgerTransaction.pending(
                command.externalReference(),
                LedgerTransactionType.valueOf(command.transactionType()),
                command.amountMinor(),
                transactionCurrency.value(),
                command.description()
        );

        var postings = command.postingLines()
                .stream()
                .map(this::toPosting)
                .toList();

        JournalEntry journalEntry = JournalEntry.create(
                transaction.id(),
                transactionCurrency.value(),
                postings
        );

        DoubleEntryPostingPolicy.validate(
                transaction.amountMinor(),
                transaction.currencyCode(),
                journalEntry.getPostings()
        );

        return new PostedLedgerGraph(transaction, journalEntry);
    }

    private Posting toPosting(PostingLineCommand line) {

        CurrencyCode lineCurrency = CurrencyCode.of(line.currencyCode());

        return new Posting(
                UUID.randomUUID(),
                line.accountId(),
                line.direction(),
                line.amountMinor(),
                lineCurrency.value()
        );
    }
}
