package dev.kavrin.banking_ledger.ledger.api;

import dev.kavrin.banking_ledger.ledger.api.dto.LedgerTransactionInvestigationResponse;
import dev.kavrin.banking_ledger.ledger.application.service.LedgerTransactionInvestigationUseCase;
import dev.kavrin.banking_ledger.ledger.domain.model.LedgerTransactionType;
import dev.kavrin.banking_ledger.ledger.domain.model.TransactionStatus;
import dev.kavrin.banking_ledger.security.auth.BankingJwtAuthenticationConverter;
import dev.kavrin.banking_ledger.security.auth.SecurityErrorHandlers;
import dev.kavrin.banking_ledger.security.config.JwtSecurityProperties;
import dev.kavrin.banking_ledger.security.config.SecurityConfig;
import dev.kavrin.banking_ledger.security.domain.SecurityRole;
import dev.kavrin.banking_ledger.security.support.TestJwtFactory;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LedgerInvestigationController.class)
@Import({
        SecurityConfig.class,
        BankingJwtAuthenticationConverter.class,
        SecurityErrorHandlers.class
})
@EnableConfigurationProperties(JwtSecurityProperties.class)
@TestPropertySource(properties = {
        "banking-ledger.security.jwt.issuer=banking-ledger-local",
        "banking-ledger.security.jwt.audience=banking-ledger-api",
        "banking-ledger.security.jwt.secret=12345678901234567890123456789012",
        "banking-ledger.security.jwt.clock-skew=30s",
        "banking-ledger.security.jwt.access-token-ttl=15m"
})
class LedgerInvestigationControllerTest {

    private static final TestJwtFactory TOKENS = new TestJwtFactory(
            "banking-ledger-local",
            "banking-ledger-api",
            "12345678901234567890123456789012",
            "wrong-secret-12345678901234567890"
    );

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LedgerTransactionInvestigationUseCase investigationUseCase;

    @Test
    void auditorCanReadLedgerInvestigation() throws Exception {
        var transactionId = UUID.randomUUID();
        when(investigationUseCase.getByTransactionId(transactionId)).thenReturn(response(transactionId));

        mockMvc.perform(get("/api/v1/ops/ledger/transactions/{transactionId}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.AUDITOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.id").value(transactionId.toString()))
                .andExpect(jsonPath("$.transaction.status").value("POSTED"));
    }

    @Test
    void serviceCanReadLedgerInvestigation() throws Exception {
        var transactionId = UUID.randomUUID();
        when(investigationUseCase.getByTransactionId(transactionId)).thenReturn(response(transactionId));

        mockMvc.perform(get("/api/v1/ops/ledger/transactions/{transactionId}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.SERVICE))))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotReadLedgerInvestigation() throws Exception {
        mockMvc.perform(get("/api/v1/ops/ledger/transactions/{transactionId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.customerToken(UUID.randomUUID()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ops/ledger/transactions/{transactionId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void invalidTransactionIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/ops/ledger/transactions/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.AUDITOR))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void missingTransactionReturnsNotFound() throws Exception {
        var transactionId = UUID.randomUUID();
        when(investigationUseCase.getByTransactionId(transactionId))
                .thenThrow(new ResourceNotFoundException("Ledger transaction not found."));

        mockMvc.perform(get("/api/v1/ops/ledger/transactions/{transactionId}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.AUDITOR))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private LedgerTransactionInvestigationResponse response(UUID transactionId) {
        var now = OffsetDateTime.now();
        return new LedgerTransactionInvestigationResponse(
                new LedgerTransactionInvestigationResponse.LedgerTransactionSummary(
                        transactionId,
                        "ext-1",
                        LedgerTransactionType.TRANSFER,
                        TransactionStatus.POSTED,
                        "USD",
                        100L,
                        "transfer",
                        now,
                        now
                ),
                null,
                List.of(),
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
