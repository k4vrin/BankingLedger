package dev.kavrin.banking_ledger.security.auth;

import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

public class BankingJwtAuthenticationToken extends AbstractAuthenticationToken {

    private final Jwt jwt;
    private final AuthenticatedPrincipal principal;

    public BankingJwtAuthenticationToken(
            Jwt jwt,
            AuthenticatedPrincipal principal,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        this.jwt = jwt;
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return jwt.getTokenValue();
    }

    @Override
    public AuthenticatedPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.subject();
    }
}
