package dev.kavrin.banking_ledger.account.api;

import dev.kavrin.banking_ledger.account.api.dto.AccountResponse;
import dev.kavrin.banking_ledger.account.api.dto.AccountTransactionSummaryResponse;
import dev.kavrin.banking_ledger.account.api.dto.BalanceResponse;
import dev.kavrin.banking_ledger.account.api.dto.CreateAccountRequest;
import dev.kavrin.banking_ledger.account.application.command.CreateAccountCommand;
import dev.kavrin.banking_ledger.account.application.query.GetAccountBalanceQuery;
import dev.kavrin.banking_ledger.account.application.query.GetAccountByIdQuery;
import dev.kavrin.banking_ledger.account.application.query.GetAccountByNumberQuery;
import dev.kavrin.banking_ledger.account.application.query.GetAccountTransactionsQuery;
import dev.kavrin.banking_ledger.account.application.service.AccountQueryUseCase;
import dev.kavrin.banking_ledger.account.application.service.CreateAccountUseCase;
import dev.kavrin.banking_ledger.account.application.service.GetAccountBalanceUseCase;
import dev.kavrin.banking_ledger.account.application.service.GetAccountTransactionsUseCase;
import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final CreateAccountUseCase createAccountUseCase;
    private final AccountQueryUseCase accountQueryUseCase;
    private final GetAccountBalanceUseCase getAccountBalanceUseCase;
    private final GetAccountTransactionsUseCase getAccountTransactionsUseCase;

    @PostMapping
    @PreAuthorize("hasAnyRole('TELLER', 'OPS_ADMIN')")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        var response = createAccountUseCase.handle(new CreateAccountCommand(
                request.customerId(),
                request.accountNumber(),
                request.accountType(),
                CurrencyCode.of(request.currencyCode()),
                principal.actorType().name(),
                principal.auditActorRole(),
                principal.actorId(),
                correlationId
        ));

        return ResponseEntity
                .created(URI.create("/api/v1/accounts/" + response.id()))
                .body(response);
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("@accountOwnership.canReadAccount(authentication.principal, #accountId)")
    public AccountResponse getById(@PathVariable UUID accountId) {
        return accountQueryUseCase.getById(new GetAccountByIdQuery(accountId));
    }

    @GetMapping("/by-number/{accountNumber}")
    @PreAuthorize("@accountOwnership.canReadAccountNumber(authentication.principal, #accountNumber)")
    public AccountResponse getByNumber(@PathVariable String accountNumber) {
        return accountQueryUseCase.getByNumber(new GetAccountByNumberQuery(accountNumber));
    }

    @GetMapping("/{accountId}/balance")
    @PreAuthorize("@accountOwnership.canReadAccount(authentication.principal, #accountId)")
    public BalanceResponse getBalance(@PathVariable UUID accountId) {
        return getAccountBalanceUseCase.handle(new GetAccountBalanceQuery(accountId));
    }

    @GetMapping("/{accountId}/transactions")
    @PreAuthorize("@accountOwnership.canReadAccount(authentication.principal, #accountId)")
    public Page<AccountTransactionSummaryResponse> getTransactions(
            @PathVariable UUID accountId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return getAccountTransactionsUseCase.handle(new GetAccountTransactionsQuery(
                accountId,
                from,
                to,
                page,
                size
        ));
    }
}
