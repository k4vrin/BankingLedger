package dev.kavrin.banking_ledger.shared.money;

import java.util.Objects;

public final class Money {
    private final long amountMinor;
    private final CurrencyCode currencyCode;

    private Money(long amountMinor, CurrencyCode currencyCode) {
        this.amountMinor = amountMinor;
        this.currencyCode = currencyCode;
    }

    public static Money ofMinor(
            long amountMinor,
            CurrencyCode currencyCode
    ) {
        if (currencyCode == null) {
            throw new IllegalArgumentException("currencyCode must not be null");
        }
        if (amountMinor < 0) {
            throw new IllegalArgumentException("amountMinor must not be negative");
        }
        return new Money(amountMinor, currencyCode);
    }

    public static Money zero(CurrencyCode currencyCode) {
        return ofMinor(0, currencyCode);
    }

    public long amountMinor() {
        return amountMinor;
    }

    public CurrencyCode currencyCode() {
        return currencyCode;
    }

    public boolean isZero() {
        return amountMinor == 0;
    }

    public boolean isPositive() {
        return amountMinor > 0;
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return Money.ofMinor(Math.addExact(this.amountMinor, other.amountMinor), this.currencyCode);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);

        if (this.amountMinor < other.amountMinor) {
            throw new IllegalArgumentException("subtraction would produce a negative money amount");
        }

        return Money.ofMinor(Math.subtractExact(this.amountMinor, other.amountMinor), this.currencyCode);
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        requireSameCurrency(other);
        return this.amountMinor >= other.amountMinor;
    }

    private void requireSameCurrency(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("other money must not be null");
        }

        if (!this.currencyCode.equals(other.currencyCode)) {
            throw new IllegalArgumentException("money arithmetic requires the same currency");
        }
    }

    @Override
    public String toString() {
        return amountMinor + " " + currencyCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;

        return amountMinor == money.amountMinor
                && Objects.equals(currencyCode, money.currencyCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amountMinor, currencyCode);
    }
}
