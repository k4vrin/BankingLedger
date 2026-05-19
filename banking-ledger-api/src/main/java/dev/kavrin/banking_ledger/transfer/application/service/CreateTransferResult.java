package dev.kavrin.banking_ledger.transfer.application.service;

import java.util.UUID;

public record CreateTransferResult(
        int statusCode,
        String responseBody,
        UUID transferId,
        boolean replayed
) {
}
