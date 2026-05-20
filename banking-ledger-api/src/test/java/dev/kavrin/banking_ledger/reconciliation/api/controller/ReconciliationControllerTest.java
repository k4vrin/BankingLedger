package dev.kavrin.banking_ledger.reconciliation.api.controller;

import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import dev.kavrin.banking_ledger.reconciliation.api.dto.ReconciliationResultSummaryResponse;
import dev.kavrin.banking_ledger.reconciliation.api.dto.SettlementBatchResponse;
import dev.kavrin.banking_ledger.reconciliation.application.command.CreateSettlementBatchCommand;
import dev.kavrin.banking_ledger.reconciliation.application.query.SearchReconciliationResultsQuery;
import dev.kavrin.banking_ledger.reconciliation.application.query.SearchSettlementBatchesQuery;
import dev.kavrin.banking_ledger.reconciliation.application.service.CreateSettlementBatchUseCase;
import dev.kavrin.banking_ledger.reconciliation.application.service.ReconciliationQueryUseCase;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationMismatchType;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationResultStatus;
import dev.kavrin.banking_ledger.reconciliation.domain.model.ReconciliationSeverity;
import dev.kavrin.banking_ledger.reconciliation.domain.model.SettlementBatchStatus;
import dev.kavrin.banking_ledger.security.domain.AuthenticatedPrincipal;
import dev.kavrin.banking_ledger.security.domain.SecurityRole;
import dev.kavrin.banking_ledger.shared.error.GlobalExceptionHandler;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReconciliationControllerTest {

    private CreateSettlementBatchUseCase createSettlementBatchUseCase;
    private ReconciliationQueryUseCase reconciliationQueryUseCase;
    private MockMvc mockMvc;
    private AuthenticatedPrincipal principal;

    @BeforeEach
    void setUp() {
        createSettlementBatchUseCase = mock(CreateSettlementBatchUseCase.class);
        reconciliationQueryUseCase = mock(ReconciliationQueryUseCase.class);
        principal = principal(SecurityRole.OPS_ADMIN);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReconciliationController(createSettlementBatchUseCase, reconciliationQueryUseCase))
                .setCustomArgumentResolvers(new PrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void postReturnsCreatedBatchAndPassesPrincipalContext() throws Exception {
        UUID batchId = UUID.randomUUID();
        when(createSettlementBatchUseCase.handle(any())).thenReturn(batchResponse(batchId));

        mockMvc.perform(post("/api/v1/ops/reconciliation/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "corr-1")
                        .content("""
                                {
                                  "source": "VISA",
                                  "referenceName": "settlement.csv",
                                  "importedByActor": "ignored-by-controller",
                                  "items": [
                                    {
                                      "externalTransactionReference": "ext-1",
                                      "amountMinor": 100,
                                      "currencyCode": "USD",
                                      "status": "SETTLED",
                                      "settlementDate": "2026-05-20"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/ops/reconciliation/batches/" + batchId))
                .andExpect(jsonPath("$.id").value(batchId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        var commandCaptor = org.mockito.ArgumentCaptor.forClass(CreateSettlementBatchCommand.class);
        verify(createSettlementBatchUseCase).handle(commandCaptor.capture());
        assertThat(commandCaptor.getValue().actorRole().name()).isEqualTo("OPS_ADMIN");
        assertThat(commandCaptor.getValue().actorId()).isEqualTo("actor-1");
        assertThat(commandCaptor.getValue().correlationId()).isEqualTo("corr-1");
    }

    @Test
    void getBatchReturnsBatch() throws Exception {
        UUID batchId = UUID.randomUUID();
        when(reconciliationQueryUseCase.getById(any())).thenReturn(batchResponse(batchId));

        mockMvc.perform(get("/api/v1/ops/reconciliation/batches/{batchId}", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(batchId.toString()));
    }

    @Test
    void unknownBatchReturnsStructuredNotFound() throws Exception {
        UUID batchId = UUID.randomUUID();
        when(reconciliationQueryUseCase.getById(any()))
                .thenThrow(new ResourceNotFoundException("Settlement batch not found.", "Settlement batch not found."));

        mockMvc.perform(get("/api/v1/ops/reconciliation/batches/{batchId}", batchId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void searchBatchesSupportsStatusFilter() throws Exception {
        UUID batchId = UUID.randomUUID();
        when(reconciliationQueryUseCase.searchBatches(any()))
                .thenReturn(new PageImpl<>(List.of(batchResponse(batchId)), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/ops/reconciliation/batches")
                        .param("status", "COMPLETED")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(batchId.toString()));

        var queryCaptor = org.mockito.ArgumentCaptor.forClass(SearchSettlementBatchesQuery.class);
        verify(reconciliationQueryUseCase).searchBatches(queryCaptor.capture());
        assertThat(queryCaptor.getValue().status()).isEqualTo(SettlementBatchStatus.COMPLETED);
        assertThat(queryCaptor.getValue().size()).isEqualTo(10);
    }

    @Test
    void searchResultsSupportsFilters() throws Exception {
        UUID batchId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        when(reconciliationQueryUseCase.searchResults(any()))
                .thenReturn(new PageImpl<>(List.of(new ReconciliationResultSummaryResponse(
                        resultId,
                        batchId,
                        UUID.randomUUID(),
                        null,
                        ReconciliationMismatchType.AMOUNT_MISMATCH,
                        ReconciliationSeverity.CRITICAL,
                        ReconciliationResultStatus.OPEN,
                        "Amount mismatch.",
                        OffsetDateTime.now(),
                        null
                )), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/ops/reconciliation/batches/{batchId}/results", batchId)
                        .param("mismatchType", "AMOUNT_MISMATCH")
                        .param("severity", "CRITICAL")
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(resultId.toString()));

        var queryCaptor = org.mockito.ArgumentCaptor.forClass(SearchReconciliationResultsQuery.class);
        verify(reconciliationQueryUseCase).searchResults(queryCaptor.capture());
        assertThat(queryCaptor.getValue().batchId()).isEqualTo(batchId);
        assertThat(queryCaptor.getValue().mismatchType()).isEqualTo(ReconciliationMismatchType.AMOUNT_MISMATCH);
        assertThat(queryCaptor.getValue().severity()).isEqualTo(ReconciliationSeverity.CRITICAL);
        assertThat(queryCaptor.getValue().status()).isEqualTo(ReconciliationResultStatus.OPEN);
    }

    @Test
    void controllerMethodsDeclareExpectedRoleRules() throws Exception {
        assertThat(preAuthorize("createBatch").value()).isEqualTo("hasAnyRole('OPS_ADMIN', 'SERVICE')");
        assertThat(preAuthorize("searchBatches").value()).isEqualTo("hasAnyRole('AUDITOR', 'OPS_ADMIN')");
        assertThat(preAuthorize("getBatch").value()).isEqualTo("hasAnyRole('AUDITOR', 'OPS_ADMIN')");
        assertThat(preAuthorize("searchResults").value()).isEqualTo("hasAnyRole('AUDITOR', 'OPS_ADMIN')");
    }

    private PreAuthorize preAuthorize(String methodName) {
        for (Method method : ReconciliationController.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method.getAnnotation(PreAuthorize.class);
            }
        }
        throw new AssertionError("Missing method " + methodName);
    }

    private SettlementBatchResponse batchResponse(UUID batchId) {
        return new SettlementBatchResponse(
                batchId,
                "VISA",
                "settlement.csv",
                "actor-1",
                "corr-1",
                SettlementBatchStatus.COMPLETED,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                1,
                1,
                0,
                List.of(),
                List.of()
        );
    }

    private AuthenticatedPrincipal principal(SecurityRole role) {
        return new AuthenticatedPrincipal(
                "subject-1",
                "actor-1",
                AuditActorType.EMPLOYEE,
                Set.of(role),
                null,
                "token-1"
        );
    }

    private class PrincipalArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(AuthenticatedPrincipal.class);
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
}
