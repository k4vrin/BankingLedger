package dev.kavrin.banking_ledger.transfer.application.query;

import java.util.UUID;

public record GetTransferByIdQuery(
        UUID transferId
) {
}
