package dev.kavrin.banking_ledger.shared.money;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    private static final CurrencyCode USD = CurrencyCode.of("USD");
    private static final CurrencyCode EUR = CurrencyCode.of("EUR");

    @Test
    void createsMoneyFromPositiveMinorUnits() {
        Money money = Money.ofMinor(1_250, USD);

        assertThat(money.amountMinor()).isEqualTo(1_250);
        assertThat(money.currencyCode()).isEqualTo(USD);
        assertThat(money.isPositive()).isTrue();
        assertThat(money.isZero()).isFalse();
    }

    @Test
    void createsZeroMoney() {
        Money money = Money.zero(USD);

        assertThat(money.amountMinor()).isZero();
        assertThat(money.currencyCode()).isEqualTo(USD);
        assertThat(money.isZero()).isTrue();
        assertThat(money.isPositive()).isFalse();
    }

    @Test
    void rejectsNullCurrency() {
        assertThatThrownBy(() -> Money.ofMinor(100, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currencyCode");
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> Money.ofMinor(-1, USD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void addsSameCurrencyMoney() {
        Money result = Money.ofMinor(100, USD).plus(Money.ofMinor(50, USD));

        assertThat(result).isEqualTo(Money.ofMinor(150, USD));
    }

    @Test
    void subtractsSameCurrencyMoney() {
        Money result = Money.ofMinor(100, USD).minus(Money.ofMinor(40, USD));

        assertThat(result).isEqualTo(Money.ofMinor(60, USD));
    }

    @Test
    void comparesSameCurrencyMoneyForSufficientBalance() {
        assertThat(Money.ofMinor(100, USD).isGreaterThanOrEqualTo(Money.ofMinor(100, USD))).isTrue();
        assertThat(Money.ofMinor(100, USD).isGreaterThanOrEqualTo(Money.ofMinor(101, USD))).isFalse();
    }

    @Test
    void rejectsCrossCurrencyArithmeticAndComparison() {
        Money usd = Money.ofMinor(100, USD);
        Money eur = Money.ofMinor(100, EUR);

        assertThatThrownBy(() -> usd.plus(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same currency");

        assertThatThrownBy(() -> usd.minus(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same currency");

        assertThatThrownBy(() -> usd.isGreaterThanOrEqualTo(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same currency");
    }

    @Test
    void rejectsSubtractionBelowZero() {
        assertThatThrownBy(() -> Money.ofMinor(50, USD).minus(Money.ofMinor(51, USD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void rejectsArithmeticOverflow() {
        assertThatThrownBy(() -> Money.ofMinor(Long.MAX_VALUE, USD).plus(Money.ofMinor(1, USD)))
                .isInstanceOf(ArithmeticException.class);
    }
}
