package dev.kavrin.banking_ledger.security.auth;

import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import dev.kavrin.banking_ledger.security.domain.SecurityRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class BankingJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new BadJwtException("JWT subject is required");
        }

        var roles = roles(jwt);
        if (roles.isEmpty()) {
            throw new BadJwtException("JWT must contain at least one recognized role");
        }

        UUID customerId = parseCustomerId(jwt.getClaimAsString("customerId"), roles);
        var actorId = normalizedClaim(jwt, "actorId", subject);
        var principal = new AuthenticatedPrincipal(
                subject,
                actorId,
                actorType(roles),
                Set.copyOf(roles),
                customerId,
                jwt.getId()
        );

        Collection<GrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .map(GrantedAuthority.class::cast)
                .toList();

        return new BankingJwtAuthenticationToken(jwt, principal, authorities);
    }

    private EnumSet<SecurityRole> roles(Jwt jwt) {
        var roles = EnumSet.noneOf(SecurityRole.class);
        Object claim = jwt.getClaims().containsKey("roles") ? jwt.getClaim("roles") : jwt.getClaim("role");

        if (claim instanceof String value) {
            addRole(roles, value);
        } else if (claim instanceof Collection<?> values) {
            values.forEach(value -> addRole(roles, String.valueOf(value)));
        }

        return roles;
    }

    private void addRole(EnumSet<SecurityRole> roles, String role) {
        if (role == null || role.isBlank()) {
            return;
        }
        var normalized = role.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        try {
            roles.add(SecurityRole.valueOf(normalized));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private UUID parseCustomerId(String customerId, Set<SecurityRole> roles) {
        if (!roles.contains(SecurityRole.CUSTOMER)) {
            return null;
        }
        if (customerId == null || customerId.isBlank()) {
            throw new BadJwtException("Customer tokens require customerId");
        }
        try {
            return UUID.fromString(customerId.trim());
        } catch (IllegalArgumentException exception) {
            throw new BadJwtException("customerId claim must be a UUID");
        }
    }

    private String normalizedClaim(Jwt jwt, String claimName, String fallback) {
        var value = jwt.getClaimAsString(claimName);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private AuditActorType actorType(Set<SecurityRole> roles) {
        if (roles.contains(SecurityRole.CUSTOMER)) {
            return AuditActorType.CUSTOMER;
        }
        if (roles.contains(SecurityRole.TELLER) || roles.contains(SecurityRole.AUDITOR) || roles.contains(SecurityRole.OPS_ADMIN)) {
            return AuditActorType.EMPLOYEE;
        }
        if (roles.contains(SecurityRole.SERVICE)) {
            return AuditActorType.SERVICE;
        }
        return AuditActorType.SYSTEM;
    }
}
