package dev.kavrin.banking_ledger.adjustment.api.dto;

import dev.kavrin.banking_ledger.adjustment.domain.model.AdjustmentReasonCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record CreateAdjustmentRequest(

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        String currencyCode,

        @Positive
        long amountMinor,

        @NotNull
        AdjustmentReasonCode reasonCode,

        @Size(max = 1000)
        String reasonDetail,

        @NotEmpty
        @Size(min = 2)
        List<@Valid AdjustmentPostingLineRequest> postingLines
) {
}