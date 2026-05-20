package dev.kavrin.banking_ledger.reconciliation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "banking-ledger.reconciliation.import")
public record ReconciliationImportProperties(
        int maxBatchSize
) {
}
