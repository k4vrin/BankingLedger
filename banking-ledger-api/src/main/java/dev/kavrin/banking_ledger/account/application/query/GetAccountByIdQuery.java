package dev.kavrin.banking_ledger.account.application.query;

import java.util.UUID;

public record GetAccountByIdQuery(
        UUID accountId
) {

}
