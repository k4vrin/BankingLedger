package dev.kavrin.banking_ledger.reconciliation.domain.policy;

import dev.kavrin.banking_ledger.reconciliation.api.dto.CreateSettlementBatchRequest;
import dev.kavrin.banking_ledger.reconciliation.api.dto.SettlementItemRequest;
import dev.kavrin.banking_ledger.reconciliation.config.ReconciliationImportProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementBatchValidationPolicyTest {

    private final SettlementBatchValidationPolicy policy =
            new SettlementBatchValidationPolicy(new ReconciliationImportProperties(2));

    @Test
    void validBatchReturnsNoErrors() {
        var request = request(
                "VISA",
                item("ext-1", 100L, "USD", "SETTLED"),
                item("ext-2", 0L, "USD", "REJECTED")
        );

        var result = policy.validate(request);

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void missingSourceReturnsStructuredError() {
        var request = new CreateSettlementBatchRequest(
                " ",
                "settlement-file.csv",
                "ops-1",
                "corr-1",
                List.of(item("ext-1", 100L, "USD", "SETTLED"))
        );

        var result = policy.validate(request);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors()).contains(new SettlementBatchValidationError("source", "source is required"));
    }

    @Test
    void emptyItemsReturnsStructuredError() {
        var request = new CreateSettlementBatchRequest(
                "VISA",
                "settlement-file.csv",
                "ops-1",
                "corr-1",
                List.of()
        );

        var result = policy.validate(request);

        assertThat(result.errors()).contains(new SettlementBatchValidationError("items", "items must not be empty"));
    }

    @Test
    void batchAboveConfiguredLimitReturnsStructuredError() {
        var request = request(
                "VISA",
                item("ext-1", 100L, "USD", "SETTLED"),
                item("ext-2", 100L, "USD", "SETTLED"),
                item("ext-3", 100L, "USD", "SETTLED")
        );

        var result = policy.validate(request);

        assertThat(result.errors()).contains(new SettlementBatchValidationError(
                "items",
                "items must contain at most 2 entries"
        ));
    }

    @Test
    void duplicateExternalReferenceInBatchReturnsStructuredError() {
        var request = request(
                "VISA",
                item("ext-1", 100L, "USD", "SETTLED"),
                item(" EXT-1 ", 100L, "USD", "SETTLED")
        );

        var result = policy.validate(request);

        assertThat(result.errors()).contains(new SettlementBatchValidationError(
                "items[1].externalTransactionReference",
                "externalTransactionReference must be unique within the batch"
        ));
    }

    @Test
    void invalidCurrencyReturnsStructuredError() {
        var request = request("VISA", item("ext-1", 100L, "US1", "SETTLED"));

        var result = policy.validate(request);

        assertThat(result.errors()).contains(new SettlementBatchValidationError(
                "items[0].currencyCode",
                "currencyCode must be exactly 3 uppercase letters"
        ));
    }

    @Test
    void settledItemRequiresPositiveAmount() {
        var request = request("VISA", item("ext-1", 0L, "USD", "SETTLED"));

        var result = policy.validate(request);

        assertThat(result.errors()).contains(new SettlementBatchValidationError(
                "items[0].amountMinor",
                "amountMinor must be positive for SETTLED settlement items"
        ));
    }

    @Test
    void nullStatusReturnsStructuredError() {
        var request = request("VISA", item("ext-1", 100L, "USD", null));

        var result = policy.validate(request);

        assertThat(result.errors()).contains(new SettlementBatchValidationError(
                "items[0].status",
                "status is required"
        ));
    }

    @Test
    void unsupportedStatusReturnsStructuredError() {
        var request = request("VISA", item("ext-1", 100L, "USD", "CAPTURED"));

        var result = policy.validate(request);

        assertThat(result.errors()).contains(new SettlementBatchValidationError(
                "items[0].status",
                "status is not supported"
        ));
    }

    private CreateSettlementBatchRequest request(String source, SettlementItemRequest... items) {
        return new CreateSettlementBatchRequest(
                source,
                "settlement-file.csv",
                "ops-1",
                "corr-1",
                List.of(items)
        );
    }

    private SettlementItemRequest item(
            String externalReference,
            Long amountMinor,
            String currencyCode,
            String status
    ) {
        return new SettlementItemRequest(
                externalReference,
                amountMinor,
                currencyCode,
                status,
                LocalDate.of(2026, 5, 20),
                Map.of("line", externalReference)
        );
    }
}
