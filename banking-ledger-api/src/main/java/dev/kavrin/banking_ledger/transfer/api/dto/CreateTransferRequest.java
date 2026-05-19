package dev.kavrin.banking_ledger.transfer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateTransferRequest(

        @NotNull(message = "sourceAccountId is required")
        UUID sourceAccountId,

        @NotNull(message = "destinationAccountId is required")
        UUID destinationAccountId,

        @NotBlank(message = "currencyCode is required")
        @Pattern(
                regexp = "^[A-Z]{3}$",
                message = "currencyCode must be exactly 3 uppercase letters"
        )
        String currencyCode,

        @NotNull(message = "amountMinor is required")
        @Positive(message = "amountMinor must be positive")
        Long amountMinor,

        @Size(max = 100, message = "externalReference must be at most 100 characters")
        String externalReference,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description
) {
}
