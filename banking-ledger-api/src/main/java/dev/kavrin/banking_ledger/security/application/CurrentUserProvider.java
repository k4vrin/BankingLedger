package dev.kavrin.banking_ledger.security.application;

import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import dev.kavrin.banking_ledger.security.domain.SecurityRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class CurrentUserProvider {

    public AuthenticatedPrincipal getCurrentUser(Authentication authentication) {

        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException(
                    "Authentication principal is not a JWT"
            );
        }

        List<String> roleClaims = Optional.ofNullable(
                jwt.getClaimAsStringList("roles")
        ).orElse(List.of());

        Set<SecurityRole> roles = roleClaims.stream()
                .map(SecurityRole::valueOf)
                .collect(Collectors.toSet());

        String customerIdClaim = jwt.getClaimAsString("customer_id");

        UUID customerId = customerIdClaim == null
                || customerIdClaim.isBlank()
                ? null
                : UUID.fromString(customerIdClaim);

        String actorId = jwt.getClaimAsString("actor_id");

        String actorTypeClaim = jwt.getClaimAsString("actor_type");

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
}