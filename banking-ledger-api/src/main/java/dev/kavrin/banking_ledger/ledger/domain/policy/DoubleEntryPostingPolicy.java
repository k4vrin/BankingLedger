package dev.kavrin.banking_ledger.ledger.domain.policy;

import dev.kavrin.banking_ledger.ledger.domain.model.Posting;
import dev.kavrin.banking_ledger.ledger.domain.model.PostingDirection;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BusinessRuleViolationException;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

@NoArgsConstructor
public final class DoubleEntryPostingPolicy {

    public static void validate(
            long transactionAmountMinor,
            String transactionCurrency,
            List<Posting> postings
    ) {

        validate(transactionCurrency, postings);

        long totalDebit = total(postings, PostingDirection.DEBIT);

        if (totalDebit != transactionAmountMinor) {
            throw unbalanced("journal total must equal transaction amount");
        }

    }

    public static void validate(
            String transactionCurrency,
            List<Posting> postings
    ) {
        Objects.requireNonNull(transactionCurrency, "transactionCurrency is required");
        transactionCurrency = transactionCurrency.trim();

        if (postings == null || postings.size() < 2) {
            throw invalid("journal entry must have at least two postings");
        }

        boolean hasDebit = false;
        boolean hasCredit = false;

        for (var posting : postings) {
            Objects.requireNonNull(posting, "posting is required");

            if (!posting.currencyCode().equals(transactionCurrency)) {
                throw unbalanced("posting currency must match transaction currency");
            }

            if (posting.amountMinor() <= 0) {
                throw invalid("posting amount must be positive");
            }

            if (posting.direction() == PostingDirection.DEBIT) {
                hasDebit = true;
            } else if (posting.direction() == PostingDirection.CREDIT) {
                hasCredit = true;
            } else {
                throw invalid("invalid posting direction: " + posting.direction());
            }
        }

        if (!hasDebit || !hasCredit) {
            throw invalid("journal entry must have at least one debit and one credit");
        }

        long totalDebit = total(postings, PostingDirection.DEBIT);
        long totalCredit = total(postings, PostingDirection.CREDIT);

        if (totalDebit != totalCredit) {
            throw unbalanced("total debit must equal total credit");
        }
    }

    private static long total(List<Posting> postings, PostingDirection direction) {

        return postings.stream()
                .filter(posting -> posting.direction() == direction)
                .mapToLong(Posting::amountMinor)
                .sum();

    }

    private static BusinessRuleViolationException invalid(String message) {
        return new BusinessRuleViolationException(
                ApiErrorCode.Business.INVALID_LEDGER_TRANSACTION,
                message,
                "Ledger transaction is invalid."
        );
    }

    private static BusinessRuleViolationException unbalanced(String message) {
        return new BusinessRuleViolationException(
                ApiErrorCode.Business.LEDGER_TRANSACTION_NOT_BALANCED,
                message,
                "Ledger transaction is not balanced."
        );
    }
}
