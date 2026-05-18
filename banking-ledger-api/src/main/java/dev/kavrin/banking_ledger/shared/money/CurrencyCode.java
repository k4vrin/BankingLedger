package dev.kavrin.banking_ledger.shared.money;

import java.util.Locale;
import java.util.Objects;

public final class CurrencyCode {
    private final String value;

    private CurrencyCode(String value) {
        this.value = value;
    }

    public static CurrencyCode of(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Currency code cannot be null or empty");
        }

        String normalized = raw.trim().toUpperCase(Locale.ROOT);

        if (!normalized.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("Invalid currency code " + raw);
        }

        return new CurrencyCode(normalized);
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CurrencyCode that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
