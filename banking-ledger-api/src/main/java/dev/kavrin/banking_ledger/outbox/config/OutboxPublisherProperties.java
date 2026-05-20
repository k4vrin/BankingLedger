package dev.kavrin.banking_ledger.outbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "banking-ledger.outbox.publisher")
public record OutboxPublisherProperties(
        boolean enabled,
        int batchSize,
        int maxAttempts,
        Duration initialRetryDelay,
        Duration maxRetryDelay
) {
}
