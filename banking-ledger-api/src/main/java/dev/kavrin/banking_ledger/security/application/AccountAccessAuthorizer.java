package dev.kavrin.banking_ledger.security.application;

import dev.kavrin.banking_ledger.account.persistence.AccountRepository;
import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import dev.kavrin.banking_ledger.security.domain.SecurityRole;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("accountOwnership")
@RequiredArgsConstructor
public class AccountAccessAuthorizer {

    private final AccountRepository accountRepository;
    private final TransferRequestRepository transferRequestRepository;

    public boolean canReadAccount(AuthenticatedPrincipal principal, UUID accountId) {
        return hasBackOfficeRead(principal) || ownsAccount(principal, accountId);
    }

    public boolean canReadAccountNumber(AuthenticatedPrincipal principal, String accountNumber) {
        return hasBackOfficeRead(principal) || ownsAccountNumber(principal, accountNumber);
    }

    public boolean canCreateTransfer(AuthenticatedPrincipal principal, UUID sourceAccountId) {
        return principal.hasRole(SecurityRole.TELLER)
                || principal.hasRole(SecurityRole.OPS_ADMIN)
                || ownsAccount(principal, sourceAccountId);
    }

    public boolean canReadTransfer(AuthenticatedPrincipal principal, UUID transferId) {
        return hasBackOfficeRead(principal)
                || principal.hasRole(SecurityRole.SERVICE)
                || (principal.customerId() != null
                && transferRequestRepository.existsByIdAndCustomerId(transferId, principal.customerId()));
    }

    private boolean hasBackOfficeRead(AuthenticatedPrincipal principal) {
        return principal.hasRole(SecurityRole.TELLER)
                || principal.hasRole(SecurityRole.AUDITOR)
                || principal.hasRole(SecurityRole.OPS_ADMIN);
    }

    private boolean ownsAccount(AuthenticatedPrincipal principal, UUID accountId) {
        return principal.customerId() != null
                && accountRepository.existsByIdAndCustomer_Id(accountId, principal.customerId());
    }

    private boolean ownsAccountNumber(AuthenticatedPrincipal principal, String accountNumber) {
        return principal.customerId() != null
                && accountRepository.existsByAccountNumberAndCustomer_Id(accountNumber, principal.customerId());
    }
}
