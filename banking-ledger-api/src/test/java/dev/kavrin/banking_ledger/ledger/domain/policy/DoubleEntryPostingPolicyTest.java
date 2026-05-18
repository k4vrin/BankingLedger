package dev.kavrin.banking_ledger.ledger.domain.policy;

import dev.kavrin.banking_ledger.ledger.domain.model.Posting;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DoubleEntryPostingPolicyTest {

    private static final UUID SOURCE_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID DESTINATION_ACCOUNT_ID = UUID.randomUUID();

    @Test
    void validDebitAndCreditPostingsAreAccepted() {
        var postings = List.of(
                Posting.debit(SOURCE_ACCOUNT_ID, 100, "USD"),
                Posting.credit(DESTINATION_ACCOUNT_ID, 100, "USD")
        );

        DoubleEntryPostingPolicy.validate(100, "USD", postings);

        assertThat(postings).hasSize(2);
    }

    @Test
    void singleSidedPostingListIsRejected() {
        assertThatThrownBy(() -> DoubleEntryPostingPolicy.validate(100, "USD", List.of(
                Posting.debit(SOURCE_ACCOUNT_ID, 100, "USD")
        )))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.INVALID_LEDGER_TRANSACTION));
    }

    @Test
    void debitOnlyPostingListIsRejected() {
        assertThatThrownBy(() -> DoubleEntryPostingPolicy.validate(100, "USD", List.of(
                Posting.debit(SOURCE_ACCOUNT_ID, 50, "USD"),
                Posting.debit(DESTINATION_ACCOUNT_ID, 50, "USD")
        )))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.INVALID_LEDGER_TRANSACTION));
    }

    @Test
    void creditOnlyPostingListIsRejected() {
        assertThatThrownBy(() -> DoubleEntryPostingPolicy.validate(100, "USD", List.of(
                Posting.credit(SOURCE_ACCOUNT_ID, 50, "USD"),
                Posting.credit(DESTINATION_ACCOUNT_ID, 50, "USD")
        )))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.INVALID_LEDGER_TRANSACTION));
    }

    @Test
    void unbalancedTotalsAreRejected() {
        assertThatThrownBy(() -> DoubleEntryPostingPolicy.validate(100, "USD", List.of(
                Posting.debit(SOURCE_ACCOUNT_ID, 100, "USD"),
                Posting.credit(DESTINATION_ACCOUNT_ID, 90, "USD")
        )))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.LEDGER_TRANSACTION_NOT_BALANCED));
    }

    @Test
    void mixedCurrenciesAreRejected() {
        assertThatThrownBy(() -> DoubleEntryPostingPolicy.validate(100, "USD", List.of(
                Posting.debit(SOURCE_ACCOUNT_ID, 100, "USD"),
                Posting.credit(DESTINATION_ACCOUNT_ID, 100, "EUR")
        )))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.LEDGER_TRANSACTION_NOT_BALANCED));
    }

    @Test
    void zeroAndNegativeAmountsAreRejectedByPostingConstruction() {
        assertThatThrownBy(() -> Posting.debit(SOURCE_ACCOUNT_ID, 0, "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amountMinor must be positive");

        assertThatThrownBy(() -> Posting.credit(DESTINATION_ACCOUNT_ID, -1, "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amountMinor must be positive");
    }

    @Test
    void transactionAmountMismatchIsRejected() {
        assertThatThrownBy(() -> DoubleEntryPostingPolicy.validate(90, "USD", List.of(
                Posting.debit(SOURCE_ACCOUNT_ID, 100, "USD"),
                Posting.credit(DESTINATION_ACCOUNT_ID, 100, "USD")
        )))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.LEDGER_TRANSACTION_NOT_BALANCED));
    }
}
