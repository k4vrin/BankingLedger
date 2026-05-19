package dev.kavrin.banking_ledger.reversal.api.dto;

import dev.kavrin.banking_ledger.reversal.domain.model.ReversalReasonCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReverseTransferRequest(

        @NotNull(message = "reasonCode is required")
        ReversalReasonCode reasonCode,

        @Size(max = 1000, message = "reasonDetail must be at most 1000 characters")
        String reasonDetail
) {
}