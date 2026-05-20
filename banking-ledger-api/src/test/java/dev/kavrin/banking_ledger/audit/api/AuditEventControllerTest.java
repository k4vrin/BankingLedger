package dev.kavrin.banking_ledger.audit.api;

import dev.kavrin.banking_ledger.audit.api.dto.AuditEventResponse;
import dev.kavrin.banking_ledger.audit.application.query.SearchAuditEventsQuery;
import dev.kavrin.banking_ledger.audit.application.service.AuditEventQueryUseCase;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEntityType;
import dev.kavrin.banking_ledger.audit.domain.model.AuditEventType;
import dev.kavrin.banking_ledger.security.auth.BankingJwtAuthenticationConverter;
import dev.kavrin.banking_ledger.security.auth.SecurityErrorHandlers;
import dev.kavrin.banking_ledger.security.config.JwtSecurityProperties;
import dev.kavrin.banking_ledger.security.config.SecurityConfig;
import dev.kavrin.banking_ledger.security.domain.SecurityRole;
import dev.kavrin.banking_ledger.security.support.TestJwtFactory;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuditEventController.class)
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
class AuditEventControllerTest {

    private static final TestJwtFactory TOKENS = new TestJwtFactory(
            "banking-ledger-local",
            "banking-ledger-api",
            "12345678901234567890123456789012",
            "wrong-secret-12345678901234567890"
    );

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditEventQueryUseCase auditEventQueryUseCase;

    @Test
    void auditorCanSearchAuditEvents() throws Exception {
        var eventId = UUID.randomUUID();
        var entityId = UUID.randomUUID();
        when(auditEventQueryUseCase.search(any(SearchAuditEventsQuery.class)))
                .thenReturn(new PageImpl<>(List.of(response(eventId, entityId))));

        mockMvc.perform(get("/api/v1/audit/events")
                        .param("eventType", "ACCOUNT_CREATED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.AUDITOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(eventId.toString()))
                .andExpect(jsonPath("$.content[0].eventType").value("ACCOUNT_CREATED"))
                .andExpect(jsonPath("$.content[0].actorRole").value("AUDITOR"));
    }

    @Test
    void opsAdminCanReadAuditEventById() throws Exception {
        var eventId = UUID.randomUUID();
        var entityId = UUID.randomUUID();
        when(auditEventQueryUseCase.getById(eventId)).thenReturn(response(eventId, entityId));

        mockMvc.perform(get("/api/v1/audit/events/{auditEventId}", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.OPS_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId.toString()));
    }

    @Test
    void nonAuditRolesCannotReadAuditEvents() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.TELLER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void invalidSearchQueryReturnsBadRequest() throws Exception {
        when(auditEventQueryUseCase.search(any(SearchAuditEventsQuery.class)))
                .thenThrow(new BadRequestException(
                        ApiErrorCode.Validation.INVALID_REQUEST,
                        "createdFrom must be before or equal to createdTo.",
                        "createdFrom must be before or equal to createdTo."
                ));

        mockMvc.perform(get("/api/v1/audit/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.AUDITOR))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void missingAuditEventReturnsNotFound() throws Exception {
        var eventId = UUID.randomUUID();
        when(auditEventQueryUseCase.getById(eventId))
                .thenThrow(new ResourceNotFoundException("Audit event not found."));

        mockMvc.perform(get("/api/v1/audit/events/{auditEventId}", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.AUDITOR))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void invalidAuditEventIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.AUDITOR))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unsupportedMutationRoutesAreNotAvailable() throws Exception {
        var eventId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/audit/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.AUDITOR))))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/v1/audit/events/{auditEventId}", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.AUDITOR))))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch("/api/v1/audit/events/{auditEventId}", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.AUDITOR))))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/v1/audit/events/{auditEventId}", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.AUDITOR))))
                .andExpect(status().isMethodNotAllowed());
    }

    private AuditEventResponse response(UUID eventId, UUID entityId) {
        return new AuditEventResponse(
                eventId,
                AuditEventType.ACCOUNT_CREATED,
                AuditEntityType.ACCOUNT,
                entityId,
                AuditActorType.EMPLOYEE,
                AuditActorRole.AUDITOR,
                "auditor-actor",
                "API",
                "corr-1",
                OffsetDateTime.now(),
                "{}"
        );
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
