package dev.kavrin.banking_ledger.reconciliation.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateSettlementBatchRequest(

        @NotBlank(message = "source is required")
        @Size(max = 100, message = "source must be at most 100 characters")
        String source,

        @NotBlank(message = "referenceName is required")
        @Size(max = 255, message = "referenceName must be at most 255 characters")
        String referenceName,

        @NotBlank(message = "importedByActor is required")
        @Size(max = 100, message = "importedByActor must be at most 100 characters")
        String importedByActor,

        @Size(max = 100, message = "correlationId must be at most 100 characters")
        String correlationId,

        @NotEmpty(message = "items must not be empty")
        List<@Valid SettlementItemRequest> items
) {
}
