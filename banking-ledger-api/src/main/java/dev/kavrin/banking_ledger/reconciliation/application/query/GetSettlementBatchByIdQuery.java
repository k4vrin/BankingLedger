package dev.kavrin.banking_ledger.reconciliation.application.query;

import java.util.UUID;

public record GetSettlementBatchByIdQuery(UUID batchId) {
}
