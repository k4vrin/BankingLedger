package dev.kavrin.banking_ledger.security.auth;

import dev.kavrin.banking_ledger.security.config.JwtSecurityProperties;
import dev.kavrin.banking_ledger.security.config.SecurityConfig;
import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import dev.kavrin.banking_ledger.security.domain.SecurityRole;
import dev.kavrin.banking_ledger.security.support.TestJwtFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = Phase7SecurityIntegrationTest.TestSecurityController.class)
@Import({
        SecurityConfig.class,
        BankingJwtAuthenticationConverter.class,
        SecurityErrorHandlers.class,
        Phase7SecurityIntegrationTest.TestSecurityController.class
})
@EnableConfigurationProperties(JwtSecurityProperties.class)
@TestPropertySource(properties = {
        "banking-ledger.security.jwt.issuer=banking-ledger-local",
        "banking-ledger.security.jwt.audience=banking-ledger-api",
        "banking-ledger.security.jwt.secret=12345678901234567890123456789012",
        "banking-ledger.security.jwt.clock-skew=30s",
        "banking-ledger.security.jwt.access-token-ttl=15m",
        "banking-ledger.security.jwt.allowed-cors-origins[0]=http://localhost:3000"
})
class Phase7SecurityIntegrationTest {

    private static final String SECRET = "12345678901234567890123456789012";
    private static final String WRONG_SECRET = "wrong-secret-12345678901234567890";
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final TestJwtFactory TOKENS = new TestJwtFactory(
            "banking-ledger-local",
            "banking-ledger-api",
            SECRET,
            WRONG_SECRET
    );

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingAuthorizationHeaderReturnsStructuredUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/test/customer"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Authentication is required."))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void nonBearerAuthorizationHeaderReturnsStructuredUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/test/customer")
                        .header(HttpHeaders.AUTHORIZATION, "Basic abc123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void malformedBearerTokenReturnsStructuredUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/test/customer")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(content().string(not(containsString("not-a-jwt"))));
    }

    @Test
    void wrongSignatureReturnsStructuredUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/test/customer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.wrongKeyCustomerToken(CUSTOMER_ID))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void expiredTokenReturnsStructuredUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/test/customer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.expiredCustomerToken(CUSTOMER_ID))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void notBeforeTokenReturnsStructuredUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/test/customer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.notBeforeCustomerToken(CUSTOMER_ID))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void invalidIssuerReturnsStructuredUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/test/customer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.invalidIssuerCustomerToken(CUSTOMER_ID))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void invalidAudienceReturnsStructuredUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/test/customer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.invalidAudienceCustomerToken(CUSTOMER_ID))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void tokenWithNoRecognizedRolesReturnsStructuredUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/test/customer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.unrecognizedRoleToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void customerTokenMapsToAuthenticatedPrincipal() throws Exception {
        mockMvc.perform(get("/api/v1/test/customer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.customerToken(CUSTOMER_ID))))
                .andExpect(status().isOk())
                .andExpect(content().string(CUSTOMER_ID.toString()));
    }

    @Test
    void authenticatedUserWithoutRequiredRoleReturnsStructuredForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/test/ops")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.AUDITOR))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void publicPathsDoNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/dev/auth/sample-users"))
                .andExpect(status().isOk())
                .andExpect(content().string("public"));
    }

    @Test
    void corsPreflightAllowsConfiguredHeadersForProtectedEndpoint() throws Exception {
        mockMvc.perform(options("/api/v1/test/customer")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Idempotency-Key,X-Correlation-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Authorization")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Idempotency-Key")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("X-Correlation-Id")));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @RestController
    static class TestSecurityController {

        @GetMapping("/api/v1/test/customer")
        @PreAuthorize("hasRole('CUSTOMER')")
        String customer(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
            return principal.customerId().toString();
        }

        @PostMapping("/api/v1/test/ops")
        @PreAuthorize("hasAnyRole('OPS_ADMIN', 'SERVICE')")
        String ops() {
            return "ok";
        }

        @GetMapping("/api/v1/dev/auth/sample-users")
        String publicDevEndpoint() {
            return "public";
        }
    }
}
