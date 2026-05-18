package dev.kavrin.banking_ledger.account.api.dto;

import java.util.UUID;

public record BalanceResponse(
        UUID accountId,
        String currencyCode,
        long availableBalanceMinor,
        long ledgerBalanceMinor
) {

}
