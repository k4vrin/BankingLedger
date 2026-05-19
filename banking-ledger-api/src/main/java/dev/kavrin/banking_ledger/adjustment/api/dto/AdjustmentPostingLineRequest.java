package dev.kavrin.banking_ledger.adjustment.api.dto;

import dev.kavrin.banking_ledger.ledger.domain.model.PostingDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record AdjustmentPostingLineRequest(

        @NotNull
        UUID accountId,

        @NotNull
        PostingDirection direction,

        @Positive
        long amountMinor,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        String currencyCode
) {
}