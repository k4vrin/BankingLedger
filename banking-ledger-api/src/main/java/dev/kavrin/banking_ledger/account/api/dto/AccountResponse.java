package dev.kavrin.banking_ledger.account.api.dto;

import dev.kavrin.banking_ledger.account.domain.model.AccountCategory;
import dev.kavrin.banking_ledger.account.domain.model.AccountStatus;
import dev.kavrin.banking_ledger.account.domain.model.AccountType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        UUID customerId,
        String accountNumber,
        AccountType accountType,
        AccountCategory accountCategory,
        AccountStatus status,
        String currencyCode,
        long availableBalanceMinor,
        long ledgerBalanceMinor,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
