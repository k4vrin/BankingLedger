package dev.kavrin.banking_ledger.audit.application.service;

import dev.kavrin.banking_ledger.audit.domain.model.AuditChannel;
import dev.kavrin.banking_ledger.security.application.CurrentUserProvider;
import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import dev.kavrin.banking_ledger.shared.correlation.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditRequestContextProvider {

    private final CurrentUserProvider currentUserProvider;

    public AuditRequestContext current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isAnonymous(authentication)) {
            return AuditRequestContext.system(CorrelationIdFilter.currentCorrelationId(), AuditChannel.SYSTEM);
        }

        AuthenticatedPrincipal principal = currentUserProvider.getCurrentUser(authentication);
        return new AuditRequestContext(
                principal.actorType(),
                principal.auditActorRole(),
                principal.actorId(),
                CorrelationIdFilter.currentCorrelationId(),
                AuditChannel.API
        );
    }

    private boolean isAnonymous(Authentication authentication) {
        return authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }
}
