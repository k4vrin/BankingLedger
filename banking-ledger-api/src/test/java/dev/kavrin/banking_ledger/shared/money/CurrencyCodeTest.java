package dev.kavrin.banking_ledger.shared.money;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrencyCodeTest {

    @Test
    void normalizesValidCurrencyCode() {
        CurrencyCode currencyCode = CurrencyCode.of(" usd ");

        assertThat(currencyCode.value()).isEqualTo("USD");
        assertThat(currencyCode.toString()).isEqualTo("USD");
    }

    @Test
    void rejectsNullCurrencyCode() {
        assertThatThrownBy(() -> CurrencyCode.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency code");
    }

    @Test
    void rejectsBlankCurrencyCode() {
        assertThatThrownBy(() -> CurrencyCode.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid currency code");
    }

    @Test
    void rejectsCurrencyCodeWithInvalidLength() {
        assertThatThrownBy(() -> CurrencyCode.of("US"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid currency code");

        assertThatThrownBy(() -> CurrencyCode.of("USDD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid currency code");
    }

    @Test
    void rejectsCurrencyCodeWithDigitsOrSymbols() {
        assertThatThrownBy(() -> CurrencyCode.of("U5D"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid currency code");

        assertThatThrownBy(() -> CurrencyCode.of("U-D"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid currency code");
    }
}
