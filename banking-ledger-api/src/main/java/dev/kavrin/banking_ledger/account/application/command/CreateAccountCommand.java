package dev.kavrin.banking_ledger.account.application.command;

import dev.kavrin.banking_ledger.account.domain.model.AccountType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;

import java.util.UUID;

public record CreateAccountCommand(
        UUID customerId,
        String accountNumber,
        AccountType accountType,
        CurrencyCode currencyCode,
        String actorType,
        AuditActorRole actorRole,
        String actorId,
        String correlationId
) {
    public CreateAccountCommand(
            UUID customerId,
            String accountNumber,
            AccountType accountType,
            CurrencyCode currencyCode,
            String actorType,
            String correlationId
    ) {
        this(customerId, accountNumber, accountType, currencyCode, actorType, null, null, correlationId);
    }
}
