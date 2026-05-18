package dev.kavrin.banking_ledger.account.application.service;

import dev.kavrin.banking_ledger.account.api.dto.AccountResponse;
import dev.kavrin.banking_ledger.account.application.command.CreateAccountCommand;
import dev.kavrin.banking_ledger.account.domain.model.AccountCategory;
import dev.kavrin.banking_ledger.account.domain.model.AccountStatus;
import dev.kavrin.banking_ledger.account.domain.model.AccountType;
import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.account.persistence.AccountRepository;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventEntity;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventRepository;
import dev.kavrin.banking_ledger.customer.persistence.CustomerRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import dev.kavrin.banking_ledger.shared.error.ConflictException;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CreateAccountUseCase {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final AuditEventRepository auditEventRepository;

    @Transactional
    public AccountResponse handle(CreateAccountCommand command) {

        var accountNumber = normalizeAccountNumber(command.accountNumber());

        validateCustomerAccountType(command.accountType());

        var customer = customerRepository.findById(command.customerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                                ApiErrorCode.Business.CUSTOMER_NOT_FOUND,
                                "Customer not found with ID: " + command.customerId(),
                                "Customer not found"
                        ));
        if (accountRepository.existsByAccountNumber(accountNumber)) {
            throw new ConflictException(
                    ApiErrorCode.Business.ACCOUNT_NUMBER_ALREADY_EXISTS,
                    "Account number already exists: " + accountNumber,
                    "Account number already exists."
            );
        }
        var currencyCode = CurrencyCode.of(command.currencyCode().value());
        var accountCategory = resolveCategory(command.accountType());
        var actorType = parseActorType(command.actorType());

        var account = AccountEntity.builder()
                .customer(customer)
                .accountNumber(accountNumber)
                .accountType(command.accountType())
                .accountCategory(accountCategory)
                .status(AccountStatus.ACTIVE)
                .currencyCode(currencyCode.value())
                .availableBalanceMinor(0L)
                .ledgerBalanceMinor(0L)
                .build();

        var savedAccount = accountRepository.save(account);

        auditEventRepository.save(AuditEventEntity.builder()
                .entityType("ACCOUNT")
                .entityId(savedAccount.getId())
                .eventType("ACCOUNT_CREATED")
                .actorType(actorType)
                .correlationId(command.correlationId())
                .build());

        return toResponse(savedAccount);

    }

    private String normalizeAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new BadRequestException(
                    ApiErrorCode.Validation.INVALID_REQUEST,
                    "Account number is required",
                    "Account number is required."
            );
        }

        var normalized = accountNumber.trim();

        if (normalized.length() > 34) {
            throw new BadRequestException(
                    ApiErrorCode.Validation.INVALID_REQUEST,
                    "Account number must be at most 34 characters",
                    "Account number must be at most 34 characters."
            );
        }

        return normalized;
    }

    private void validateCustomerAccountType(AccountType accountType) {
        if (accountType == null) {
            throw new BadRequestException(
                    ApiErrorCode.Validation.INVALID_REQUEST,
                    "Account type is required",
                    "Account type is required."
            );
        }

        if (resolveCategory(accountType) == AccountCategory.INTERNAL) {
            throw new BadRequestException(
                    ApiErrorCode.Business.INVALID_ACCOUNT_TYPE,
                    "Customer accounts cannot use internal-only account type: " + accountType,
                    "Customer accounts cannot use that account type."
            );
        }
    }

    private AuditActorType parseActorType(String actorType) {
        if (actorType == null || actorType.isBlank()) {
            return AuditActorType.SYSTEM;
        }

        try {
            return AuditActorType.valueOf(actorType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    ApiErrorCode.Validation.INVALID_REQUEST,
                    "Invalid actor type: " + actorType,
                    "Invalid actor type."
            );
        }
    }

    private AccountCategory resolveCategory(AccountType accountType) {
        return switch (accountType) {
            case CURRENT, SAVINGS, WALLET -> AccountCategory.CUSTOMER;
            case SUSPENSE, CLEARING, FEE_INCOME -> AccountCategory.INTERNAL;
        };

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
