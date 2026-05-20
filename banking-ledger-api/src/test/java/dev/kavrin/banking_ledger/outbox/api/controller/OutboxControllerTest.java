package dev.kavrin.banking_ledger.outbox.api.controller;

import dev.kavrin.banking_ledger.outbox.api.dto.OutboxEventResponse;
import dev.kavrin.banking_ledger.outbox.application.command.RequeueOutboxEventCommand;
import dev.kavrin.banking_ledger.outbox.application.service.OutboxQueryUseCase;
import dev.kavrin.banking_ledger.outbox.application.service.RequeueOutboxEventUseCase;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxStatus;
import dev.kavrin.banking_ledger.security.auth.BankingJwtAuthenticationConverter;
import dev.kavrin.banking_ledger.security.auth.SecurityErrorHandlers;
import dev.kavrin.banking_ledger.security.config.JwtSecurityProperties;
import dev.kavrin.banking_ledger.security.config.SecurityConfig;
import dev.kavrin.banking_ledger.security.domain.SecurityRole;
import dev.kavrin.banking_ledger.security.support.TestJwtFactory;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OutboxController.class)
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
class OutboxControllerTest {

    private static final TestJwtFactory TOKENS = new TestJwtFactory(
            "banking-ledger-local",
            "banking-ledger-api",
            "12345678901234567890123456789012",
            "wrong-secret-12345678901234567890"
    );

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OutboxQueryUseCase outboxQueryUseCase;

    @MockBean
    private RequeueOutboxEventUseCase requeueOutboxEventUseCase;

    @Test
    void opsAdminCanRequeueOutboxEvent() throws Exception {
        var eventId = UUID.randomUUID();
        when(requeueOutboxEventUseCase.handle(any(RequeueOutboxEventCommand.class)))
                .thenReturn(response(eventId, OutboxStatus.PENDING));
        ArgumentCaptor<RequeueOutboxEventCommand> commandCaptor =
                ArgumentCaptor.forClass(RequeueOutboxEventCommand.class);

        mockMvc.perform(post("/api/v1/ops/outbox/events/{eventId}/requeue", eventId)
                        .param("resetRetryCount", "true")
                        .header("X-Correlation-Id", "corr-requeue")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.OPS_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        org.mockito.Mockito.verify(requeueOutboxEventUseCase).handle(commandCaptor.capture());
        assertThat(commandCaptor.getValue().resetRetryCount()).isTrue();
        assertThat(commandCaptor.getValue().correlationId()).isEqualTo("corr-requeue");
    }

    @Test
    void serviceCanForceRequeueOutboxEvent() throws Exception {
        var eventId = UUID.randomUUID();
        when(requeueOutboxEventUseCase.handle(any(RequeueOutboxEventCommand.class)))
                .thenReturn(response(eventId, OutboxStatus.PENDING));

        mockMvc.perform(post("/api/v1/ops/outbox/events/{eventId}/requeue", eventId)
                        .param("force", "true")
                        .header("X-Correlation-Id", "corr-requeue")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.SERVICE))))
                .andExpect(status().isOk());
    }

    @Test
    void auditorCanQueryOutboxEvents() throws Exception {
        var eventId = UUID.randomUUID();
        when(outboxQueryUseCase.searchByStatus(OutboxStatus.DEAD_LETTER, 0, 20))
                .thenReturn(new PageImpl<>(List.of(response(eventId, OutboxStatus.DEAD_LETTER))));

        mockMvc.perform(get("/api/v1/ops/outbox/events")
                        .param("status", "DEAD_LETTER")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.AUDITOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(eventId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("DEAD_LETTER"));
    }

    @Test
    void missingEventReturnsStructuredNotFound() throws Exception {
        var eventId = UUID.randomUUID();
        when(outboxQueryUseCase.getById(eventId)).thenThrow(new ResourceNotFoundException("Outbox event not found."));

        mockMvc.perform(get("/api/v1/ops/outbox/events/{eventId}", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.AUDITOR))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void missingCorrelationIdIsRejectedForRequeue() throws Exception {
        mockMvc.perform(post("/api/v1/ops/outbox/events/{eventId}/requeue", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.OPS_ADMIN))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void customerAndTellerCannotRequeue() throws Exception {
        mockMvc.perform(post("/api/v1/ops/outbox/events/{eventId}/requeue", UUID.randomUUID())
                        .header("X-Correlation-Id", "corr-requeue")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.customerToken(UUID.randomUUID()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ops/outbox/events/{eventId}/requeue", UUID.randomUUID())
                        .header("X-Correlation-Id", "corr-requeue")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.TELLER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditorCannotRequeue() throws Exception {
        mockMvc.perform(post("/api/v1/ops/outbox/events/{eventId}/requeue", UUID.randomUUID())
                        .header("X-Correlation-Id", "corr-requeue")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.roleToken(SecurityRole.AUDITOR))))
                .andExpect(status().isForbidden());
    }

    private OutboxEventResponse response(UUID eventId, OutboxStatus status) {
        return new OutboxEventResponse(
                eventId,
                "LEDGER_TRANSACTION",
                UUID.randomUUID(),
                "LedgerTransactionPosted",
                "banking-ledger.ledger-events",
                "corr-1",
                status,
                0,
                null,
                null,
                OffsetDateTime.now(),
                null
        );
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
