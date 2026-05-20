package dev.kavrin.banking_ledger.adjustment.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.adjustment.api.dto.AdjustmentResponse;
import dev.kavrin.banking_ledger.adjustment.application.command.CreateAdjustmentCommand;
import dev.kavrin.banking_ledger.adjustment.application.service.CreateAdjustmentUseCase;
import dev.kavrin.banking_ledger.adjustment.domain.model.AdjustmentReasonCode;
import dev.kavrin.banking_ledger.adjustment.domain.model.AdjustmentStatus;
import dev.kavrin.banking_ledger.adjustment.domain.policy.AdjustmentValidationPolicy;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import dev.kavrin.banking_ledger.security.domain.SecurityRole;
import dev.kavrin.banking_ledger.shared.error.GlobalExceptionHandler;
import dev.kavrin.banking_ledger.transfer.domain.model.RequestedByActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdjustmentControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StubCreateAdjustmentUseCase createAdjustmentUseCase;
    private MockMvc mockMvc;
    private AuthenticatedPrincipal principal;

    @BeforeEach
    void setUp() {
        createAdjustmentUseCase = new StubCreateAdjustmentUseCase();
        principal = opsAdminPrincipal();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdjustmentController(createAdjustmentUseCase))
                .setCustomArgumentResolvers(new TestPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createAdjustmentMapsHeadersAndReturnsCreatedResponse() throws Exception {
        var adjustmentId = UUID.randomUUID();
        var ledgerTransactionId = UUID.randomUUID();
        var debitAccountId = UUID.randomUUID();
        var creditAccountId = UUID.randomUUID();
        createAdjustmentUseCase.nextResponse = response(adjustmentId, ledgerTransactionId);

        mockMvc.perform(post("/api/v1/ops/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", " corr-adjustment ")
                        .content(objectMapper.writeValueAsString(validBody(debitAccountId, creditAccountId))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/ops/adjustments/" + adjustmentId)))
                .andExpect(jsonPath("$.id").value(adjustmentId.toString()))
                .andExpect(jsonPath("$.ledgerTransactionId").value(ledgerTransactionId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(createAdjustmentUseCase.lastCommand.currencyCode()).isEqualTo("USD");
        assertThat(createAdjustmentUseCase.lastCommand.amountMinor()).isEqualTo(100);
        assertThat(createAdjustmentUseCase.lastCommand.reasonCode()).isEqualTo(AdjustmentReasonCode.MANUAL_CORRECTION);
        assertThat(createAdjustmentUseCase.lastCommand.reasonDetail()).isEqualTo("manual correction");
        assertThat(createAdjustmentUseCase.lastCommand.actorType()).isEqualTo(RequestedByActorType.OPS_ADMIN);
        assertThat(createAdjustmentUseCase.lastCommand.actorRole()).isEqualTo(AuditActorRole.OPS_ADMIN);
        assertThat(createAdjustmentUseCase.lastCommand.actorId()).isEqualTo("ops-user-1");
        assertThat(createAdjustmentUseCase.lastCommand.correlationId()).isEqualTo("corr-adjustment");
        assertThat(createAdjustmentUseCase.lastCommand.postingLines()).hasSize(2);
    }

    @Test
    void missingPostingLinesReturnsStructuredValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/ops/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "corr-adjustment")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currencyCode", "USD",
                                "amountMinor", 100,
                                "reasonCode", "MANUAL_CORRECTION"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed."));

        assertThat(createAdjustmentUseCase.lastCommand).isNull();
    }

    @Test
    void unsupportedReasonCodeReturnsMalformedRequest() throws Exception {
        var body = validBody(UUID.randomUUID(), UUID.randomUUID());
        body.put("reasonCode", "UNKNOWN_REASON");

        mockMvc.perform(post("/api/v1/ops/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "corr-adjustment")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed."));

        assertThat(createAdjustmentUseCase.lastCommand).isNull();
    }

    @Test
    void missingCorrelationHeaderReturnsStructuredBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/ops/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody(UUID.randomUUID(), UUID.randomUUID()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("X-Correlation-Id"));

        assertThat(createAdjustmentUseCase.lastCommand).isNull();
    }

    @Test
    void forbiddenActorRoleReturnsStructuredError() throws Exception {
        principal = tellerPrincipal();

        mockMvc.perform(post("/api/v1/ops/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "corr-adjustment")
                        .content(objectMapper.writeValueAsString(validBody(UUID.randomUUID(), UUID.randomUUID()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_RESOURCE"))
                .andExpect(jsonPath("$.message").value("Actor role is not allowed to post adjustments."));
    }

    private Map<String, Object> validBody(UUID debitAccountId, UUID creditAccountId) {
        return new java.util.LinkedHashMap<>(Map.of(
                "currencyCode", "USD",
                "amountMinor", 100,
                "reasonCode", "MANUAL_CORRECTION",
                "reasonDetail", " manual correction ",
                "postingLines", List.of(
                        Map.of(
                                "accountId", debitAccountId,
                                "direction", "DEBIT",
                                "amountMinor", 100,
                                "currencyCode", "USD"
                        ),
                        Map.of(
                                "accountId", creditAccountId,
                                "direction", "CREDIT",
                                "amountMinor", 100,
                                "currencyCode", "USD"
                        )
                )
        ));
    }

    private AdjustmentResponse response(UUID adjustmentId, UUID ledgerTransactionId) {
        return new AdjustmentResponse(
                adjustmentId,
                ledgerTransactionId,
                AdjustmentReasonCode.MANUAL_CORRECTION,
                "manual correction",
                RequestedByActorType.OPS_ADMIN,
                AuditActorRole.OPS_ADMIN,
                "ops-user-1",
                "corr-adjustment",
                AdjustmentStatus.COMPLETED,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private AuthenticatedPrincipal opsAdminPrincipal() {
        return new AuthenticatedPrincipal(
                "ops-subject",
                "ops-user-1",
                AuditActorType.EMPLOYEE,
                Set.of(SecurityRole.OPS_ADMIN),
                null,
                "token-id"
        );
    }

    private AuthenticatedPrincipal tellerPrincipal() {
        return new AuthenticatedPrincipal(
                "teller-subject",
                "teller-user-1",
                AuditActorType.EMPLOYEE,
                Set.of(SecurityRole.TELLER),
                null,
                "token-id"
        );
    }

    private class TestPrincipalArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return AuthenticatedPrincipal.class.equals(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory
        ) {
            return principal;
        }
    }

    private static class StubCreateAdjustmentUseCase extends CreateAdjustmentUseCase {
        private CreateAdjustmentCommand lastCommand;
        private AdjustmentResponse nextResponse;

        private StubCreateAdjustmentUseCase() {
            super(null, null, null, null, null, null);
        }

        @Override
        public AdjustmentResponse handle(CreateAdjustmentCommand command) {
            lastCommand = command;
            AdjustmentValidationPolicy.validateRequest(command);
            return nextResponse;
        }
    }
}
