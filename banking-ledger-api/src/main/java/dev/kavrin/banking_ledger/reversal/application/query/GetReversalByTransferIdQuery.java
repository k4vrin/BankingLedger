package dev.kavrin.banking_ledger.reversal.application.query;

import java.util.Objects;
import java.util.UUID;

public record GetReversalByTransferIdQuery(
        UUID transferId
) {
    public GetReversalByTransferIdQuery {
        Objects.requireNonNull(transferId, "transferId is required");
    }
}