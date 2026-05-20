package dev.kavrin.banking_ledger.audit.domain.model;

import java.util.UUID;

public record TransferReversedAuditPayload(
        UUID reversalId,
        UUID originalTransferId,
        UUID originalLedgerTransactionId,
        UUID reversalLedgerTransactionId,
        String reasonCode
) {
}
