package dev.kavrin.banking_ledger.security.application;

import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import dev.kavrin.banking_ledger.security.domain.SecurityRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class CurrentUserProvider {

    public AuthenticatedPrincipal getCurrentUser(Authentication authentication) {

        if (authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            return principal;
        }

        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException(
                    "Authentication principal is not a JWT"
            );
        }

        List<String> roleClaims = Optional.ofNullable(jwt.getClaimAsStringList("roles"))
                .or(() -> Optional.ofNullable(jwt.getClaimAsStringList("role")))
                .orElse(List.of());

        Set<SecurityRole> roles = roleClaims.stream()
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                .map(SecurityRole::valueOf)
                .collect(Collectors.toSet());

        String customerIdClaim = firstClaim(jwt, "customerId", "customer_id");

        UUID customerId = customerIdClaim == null
                || customerIdClaim.isBlank()
                ? null
                : UUID.fromString(customerIdClaim);

        String actorId = firstClaim(jwt, "actorId", "actor_id");

        String actorTypeClaim = firstClaim(jwt, "actorType", "actor_type");

        AuditActorType actorType = actorTypeClaim == null
                || actorTypeClaim.isBlank()
                ? AuditActorType.SYSTEM
                : AuditActorType.valueOf(actorTypeClaim);

        String tokenId = jwt.getId();

        return new AuthenticatedPrincipal(
                jwt.getSubject(),
                actorId,
                actorType,
                roles,
                customerId,
                tokenId
        );
    }

    private String firstClaim(Jwt jwt, String first, String second) {
        var value = jwt.getClaimAsString(first);
        return value == null || value.isBlank() ? jwt.getClaimAsString(second) : value;
    }
}
