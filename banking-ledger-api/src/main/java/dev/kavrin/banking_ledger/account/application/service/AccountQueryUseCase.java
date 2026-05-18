package dev.kavrin.banking_ledger.account.application.service;

import dev.kavrin.banking_ledger.account.api.dto.AccountResponse;
import dev.kavrin.banking_ledger.account.application.query.GetAccountByIdQuery;
import dev.kavrin.banking_ledger.account.application.query.GetAccountByNumberQuery;
import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.account.persistence.AccountRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountQueryUseCase {

    private final AccountRepository accountRepository;

    public AccountResponse getById(GetAccountByIdQuery query) {
        var account = accountRepository.findById(query.accountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ApiErrorCode.Business.ACCOUNT_NOT_FOUND,
                        "Account not found with id: " + query.accountId(),
                        "Account not found."
                ));

        return toResponse(account);
    }

    public AccountResponse getByNumber(GetAccountByNumberQuery query) {
        var account = accountRepository.findByAccountNumber(query.accountNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ApiErrorCode.Business.ACCOUNT_NOT_FOUND,
                        "Account not found with number: " + query.accountNumber(),
                        "Account not found."
                ));

        return toResponse(account);
    }

    private AccountResponse toResponse(AccountEntity account) {
        return new AccountResponse(
                account.getId(),
                account.getCustomer().getId(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getAccountCategory(),
                account.getStatus(),
                account.getCurrencyCode(),
                account.getAvailableBalanceMinor(),
                account.getLedgerBalanceMinor(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
