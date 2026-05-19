package dev.kavrin.banking_ledger.idempotency.application.service;

import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyKeyValidator {

    private static final int MAX_LENGTH = 128;

    public String validateAndNormalize(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }

        var normalized = key.trim();
        if (normalized.length() > MAX_LENGTH) {
            throw new BadRequestException("Idempotency-Key must be at most 128 characters");
        }

        return normalized;
    }
}
