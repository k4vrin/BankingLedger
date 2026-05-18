package dev.kavrin.banking_ledger.account.application.service;

import dev.kavrin.banking_ledger.account.api.dto.BalanceResponse;
import dev.kavrin.banking_ledger.account.application.query.GetAccountBalanceQuery;
import dev.kavrin.banking_ledger.account.persistence.AccountRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetAccountBalanceUseCase {

    private final AccountRepository accountRepository;

    public BalanceResponse handle(GetAccountBalanceQuery query) {
        var account = accountRepository.findById(query.accountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ApiErrorCode.Business.ACCOUNT_NOT_FOUND,
                        "Account not found with id: " + query.accountId(),
                        "Account not found."
                ));

        return new BalanceResponse(
                account.getId(),
                account.getCurrencyCode(),
                account.getAvailableBalanceMinor(),
                account.getLedgerBalanceMinor()
        );
    }
}
