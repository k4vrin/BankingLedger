package dev.kavrin.banking_ledger.reconciliation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Map;

public record SettlementItemRequest(

        @NotBlank(message = "externalTransactionReference is required")
        @Size(max = 100, message = "externalTransactionReference must be at most 100 characters")
        String externalTransactionReference,

        @NotNull(message = "amountMinor is required")
        Long amountMinor,

        @NotBlank(message = "currencyCode is required")
        @Pattern(
                regexp = "^[A-Z]{3}$",
                message = "currencyCode must be exactly 3 uppercase letters"
        )
        String currencyCode,

        @NotBlank(message = "status is required")
        String status,

        @NotNull(message = "settlementDate is required")
        LocalDate settlementDate,

        Map<String, Object> metadata
) {
}
