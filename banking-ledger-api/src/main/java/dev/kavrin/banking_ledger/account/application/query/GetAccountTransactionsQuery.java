package dev.kavrin.banking_ledger.account.application.query;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GetAccountTransactionsQuery(
        UUID accountId,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size

) {

}