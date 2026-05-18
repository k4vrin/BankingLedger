package dev.kavrin.banking_ledger.account.api.dto;

import dev.kavrin.banking_ledger.account.domain.model.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateAccountRequest(

        @NotNull(message = "customerId is required")
        UUID customerId,

        @NotBlank(message = "accountNumber is required")
        @Size(max = 34, message = "accountNumber must be at most 34 characters")
        String accountNumber,

        @NotNull(message = "accountType is required")
        AccountType accountType,

        @NotBlank(message = "currencyCode is required")
        @Pattern(
                regexp = "^[A-Z]{3}$",
                message = "currencyCode must be exactly 3 uppercase letters"
        )
        String currencyCode
) {
}