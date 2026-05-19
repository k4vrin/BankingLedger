package dev.kavrin.banking_ledger.account.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.account.application.command.CreateAccountCommand;
import dev.kavrin.banking_ledger.account.application.service.CreateAccountUseCase;
import dev.kavrin.banking_ledger.account.domain.model.AccountType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.customer.domain.model.CustomerStatus;
import dev.kavrin.banking_ledger.customer.persistence.CustomerEntity;
import dev.kavrin.banking_ledger.customer.persistence.CustomerRepository;
import dev.kavrin.banking_ledger.security.auth.BankingJwtAuthenticationToken;
import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import dev.kavrin.banking_ledger.security.domain.SecurityRole;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CreateAccountUseCase createAccountUseCase;

    @Test
    void createAccountReturnsCreatedAccount() throws Exception {
        var customer = createCustomer();
        var accountNumber = "API-" + shortSuffix();

        mockMvc.perform(post("/api/v1/accounts")
                        .with(csrf())
                        .with(authentication(auth(SecurityRole.TELLER, null)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "corr-api-create")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", customer.getId(),
                                "accountNumber", accountNumber,
                                "accountType", "CURRENT",
                                "currencyCode", "USD"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/accounts/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.customerId").value(customer.getId().toString()))
                .andExpect(jsonPath("$.accountNumber").value(accountNumber))
                .andExpect(jsonPath("$.accountType").value("CURRENT"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.availableBalanceMinor").value(0))
                .andExpect(jsonPath("$.ledgerBalanceMinor").value(0));
    }

    @Test
    void invalidCreateAccountRequestReturnsStructuredValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .with(csrf())
                        .with(authentication(auth(SecurityRole.TELLER, null)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountNumber", "",
                                "accountType", "CURRENT",
                                "currencyCode", "usd"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed."))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("customerId")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("accountNumber")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("currencyCode")));
    }

    @Test
    void duplicateAccountNumberReturnsDomainConflictError() throws Exception {
        var customer = createCustomer();
        var accountNumber = "DUPAPI-" + shortSuffix();
        createAccount(customer.getId(), accountNumber);

        mockMvc.perform(post("/api/v1/accounts")
                        .with(csrf())
                        .with(authentication(auth(SecurityRole.TELLER, null)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", customer.getId(),
                                "accountNumber", accountNumber,
                                "accountType", "CURRENT",
                                "currencyCode", "USD"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NUMBER_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Account number already exists."));
    }

    @Test
    void getAccountAndBalanceReturnResponses() throws Exception {
        var customer = createCustomer();
        var account = createAccount(customer.getId(), "GET-" + shortSuffix());

        mockMvc.perform(get("/api/v1/accounts/{accountId}", account.id())
                        .with(authentication(auth(SecurityRole.CUSTOMER, customer.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.id().toString()))
                .andExpect(jsonPath("$.accountNumber").value(account.accountNumber()));

        mockMvc.perform(get("/api/v1/accounts/{accountId}/balance", account.id())
                        .with(authentication(auth(SecurityRole.CUSTOMER, customer.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.id().toString()))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.availableBalanceMinor").value(0))
                .andExpect(jsonPath("$.ledgerBalanceMinor").value(0));
    }

    @Test
    void getTransactionHistoryReturnsPaginatedResponse() throws Exception {
        var customer = createCustomer();
        var account = createAccount(customer.getId(), "PAGE-" + shortSuffix());

        mockMvc.perform(get("/api/v1/accounts/{accountId}/transactions", account.id())
                        .with(authentication(auth(SecurityRole.CUSTOMER, customer.getId())))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private dev.kavrin.banking_ledger.account.api.dto.AccountResponse createAccount(UUID customerId, String accountNumber) {
        return createAccountUseCase.handle(new CreateAccountCommand(
                customerId,
                accountNumber,
                AccountType.CURRENT,
                CurrencyCode.of("USD"),
                "SYSTEM",
                "corr-api-helper"
        ));
    }

    private CustomerEntity createCustomer() {
        return customerRepository.save(CustomerEntity.builder()
                .externalCustomerReference("api-cust-" + shortSuffix())
                .fullName("Phase 2 API Customer")
                .email("phase2-api-" + shortSuffix() + "@example.com")
                .status(CustomerStatus.ACTIVE)
                .build());
    }

    private BankingJwtAuthenticationToken auth(SecurityRole role, UUID customerId) {
        var principal = new AuthenticatedPrincipal(
                "test-" + role.name().toLowerCase(),
                "test-actor",
                actorType(role),
                Set.of(role),
                customerId,
                "token-id"
        );
        return new BankingJwtAuthenticationToken(
                jwt(),
                principal,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    private AuditActorType actorType(SecurityRole role) {
        return switch (role) {
            case CUSTOMER -> AuditActorType.CUSTOMER;
            case SERVICE -> AuditActorType.SERVICE;
            case TELLER, AUDITOR, OPS_ADMIN -> AuditActorType.EMPLOYEE;
        };
    }

    private Jwt jwt() {
        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of("sub", "test-subject")
        );
    }

    private static String shortSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
