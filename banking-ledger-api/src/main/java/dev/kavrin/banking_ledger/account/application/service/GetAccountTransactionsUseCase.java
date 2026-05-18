package dev.kavrin.banking_ledger.account.application.service;


import dev.kavrin.banking_ledger.account.api.dto.AccountTransactionSummaryResponse;
import dev.kavrin.banking_ledger.account.application.query.GetAccountTransactionsQuery;
import dev.kavrin.banking_ledger.account.persistence.AccountRepository;
import dev.kavrin.banking_ledger.ledger.persistence.PostingEntity;
import dev.kavrin.banking_ledger.ledger.persistence.PostingRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetAccountTransactionsUseCase {

    private final PostingRepository postingRepository;
    private final AccountRepository accountRepository;

    public Page<AccountTransactionSummaryResponse> handle(GetAccountTransactionsQuery query) {
        validatePageRequest(query);
        validateDateRange(query);
        ensureAccountExists(query);

        var pageable = PageRequest.of(query.page(), query.size());

        return postingRepository.findAccountTransactionHistory(
                query.accountId(),
                query.from(),
                query.to(),
                pageable
        ).map(this::toResponse);
    }

    private AccountTransactionSummaryResponse toResponse(PostingEntity posting) {
        return new AccountTransactionSummaryResponse(
                posting.getId(),
                posting.getJournalEntry().getLedgerTransaction().getId(),
                posting.getDirection(),
                posting.getAmountMinor(),
                posting.getCurrencyCode(),
                posting.getJournalEntry().getDescription(),
                posting.getPostedAt()
        );

    }

    private void validatePageRequest(GetAccountTransactionsQuery query) {
        if (query.page() < 0) {
            throw new BadRequestException(
                    ApiErrorCode.Validation.INVALID_REQUEST,
                    "Page index must not be negative",
                    "Page index must not be negative."
            );
        }

        if (query.size() < 1 || query.size() > 100) {
            throw new BadRequestException(
                    ApiErrorCode.Validation.INVALID_REQUEST,
                    "Page size must be between 1 and 100",
                    "Page size must be between 1 and 100."
            );
        }
    }

    private void validateDateRange(GetAccountTransactionsQuery query) {
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new BadRequestException(
                    ApiErrorCode.Validation.INVALID_REQUEST,
                    "Transaction history from timestamp must not be after to timestamp",
                    "Invalid transaction history date range."
            );
        }
    }

    private void ensureAccountExists(GetAccountTransactionsQuery query) {
        if (!accountRepository.existsById(query.accountId())) {
            throw new ResourceNotFoundException(
                    ApiErrorCode.Business.ACCOUNT_NOT_FOUND,
                    "Account not found with id: " + query.accountId(),
                    "Account not found."
            );
        }
    }

}
