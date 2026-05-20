package dev.kavrin.banking_ledger.security.auth;

import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import dev.kavrin.banking_ledger.security.domain.SecurityRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankingJwtAuthenticationConverterTest {

    private final BankingJwtAuthenticationConverter converter = new BankingJwtAuthenticationConverter();

    @Test
    void mapsCustomerTokenToPrincipalAndAuthorities() {
        var customerId = UUID.randomUUID();
        var authentication = converter.convert(jwt(Map.of(
                "sub", "customer-subject",
                "roles", List.of("CUSTOMER"),
                "customerId", customerId.toString(),
                "actorId", "customer-actor",
                "jti", "token-id"
        )));

        var principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        assertThat(principal.subject()).isEqualTo("customer-subject");
        assertThat(principal.actorId()).isEqualTo("customer-actor");
        assertThat(principal.actorType()).isEqualTo(AuditActorType.CUSTOMER);
        assertThat(principal.roles()).containsExactly(SecurityRole.CUSTOMER);
        assertThat(principal.customerId()).isEqualTo(customerId);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void mapsSingleRoleClaim() {
        var authentication = converter.convert(jwt(Map.of(
                "sub", "ops-subject",
                "role", "ROLE_OPS_ADMIN"
        )));

        var principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        assertThat(principal.roles()).containsExactly(SecurityRole.OPS_ADMIN);
        assertThat(principal.actorType()).isEqualTo(AuditActorType.EMPLOYEE);
    }

    @Test
    void mapsMultipleRoles() {
        var authentication = converter.convert(jwt(Map.of(
                "sub", "multi-role-subject",
                "roles", List.of("AUDITOR", "OPS_ADMIN")
        )));

        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_AUDITOR", "ROLE_OPS_ADMIN");
    }

    @Test
    void rejectsMissingSubject() {
        assertThatThrownBy(() -> converter.convert(jwt(Map.of("roles", List.of("SERVICE")))))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("subject");
    }

    @Test
    void rejectsUnrecognizedRoles() {
        assertThatThrownBy(() -> converter.convert(jwt(Map.of(
                "sub", "unknown-subject",
                "roles", List.of("UNKNOWN")
        ))))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("recognized role");
    }

    @Test
    void rejectsCustomerTokenWithoutCustomerId() {
        assertThatThrownBy(() -> converter.convert(jwt(Map.of(
                "sub", "customer-subject",
                "roles", List.of("CUSTOMER")
        ))))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("customerId");
    }

    @Test
    void rejectsMalformedCustomerId() {
        assertThatThrownBy(() -> converter.convert(jwt(Map.of(
                "sub", "customer-subject",
                "roles", List.of("CUSTOMER"),
                "customerId", "not-a-uuid"
        ))))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("UUID");
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"),
                claims
        );
    }
}
