package dev.kavrin.banking_ledger.audit.application.service;

import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditChannel;
import dev.kavrin.banking_ledger.security.application.CurrentUserProvider;
import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import dev.kavrin.banking_ledger.security.domain.SecurityRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuditRequestContextProviderTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentReturnsSystemContextWithoutAuthentication() {
        var provider = new AuditRequestContextProvider(new CurrentUserProvider());

        var context = provider.current();

        assertThat(context.actorType()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(context.actorRole().name()).isEqualTo("SYSTEM");
        assertThat(context.actorId()).isEqualTo("system");
        assertThat(context.channel()).isEqualTo(AuditChannel.SYSTEM);
    }

    @Test
    void currentReturnsPrincipalActorContext() {
        var principal = new AuthenticatedPrincipal(
                "subject-1",
                "actor-1",
                AuditActorType.EMPLOYEE,
                Set.of(SecurityRole.OPS_ADMIN),
                null,
                "token-1"
        );
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())
        );
        var provider = new AuditRequestContextProvider(new CurrentUserProvider());

        var context = provider.current();

        assertThat(context.actorType()).isEqualTo(AuditActorType.EMPLOYEE);
        assertThat(context.actorRole().name()).isEqualTo("OPS_ADMIN");
        assertThat(context.actorId()).isEqualTo("actor-1");
        assertThat(context.channel()).isEqualTo(AuditChannel.API);
    }
}
