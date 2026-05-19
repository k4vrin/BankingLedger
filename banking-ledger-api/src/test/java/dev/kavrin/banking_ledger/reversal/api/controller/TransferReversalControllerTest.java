package dev.kavrin.banking_ledger.reversal.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.reversal.api.dto.ReversalResponse;
import dev.kavrin.banking_ledger.reversal.application.command.ReverseTransferCommand;
import dev.kavrin.banking_ledger.reversal.application.service.ReverseTransferUseCase;
import dev.kavrin.banking_ledger.reversal.domain.model.ReversalReasonCode;
import dev.kavrin.banking_ledger.reversal.domain.model.ReversalStatus;
import dev.kavrin.banking_ledger.shared.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransferReversalControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StubReverseTransferUseCase reverseTransferUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reverseTransferUseCase = new StubReverseTransferUseCase();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TransferReversalController(reverseTransferUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void reverseTransferMapsHeadersAndReturnsCreatedResponse() throws Exception {
        var transferId = UUID.randomUUID();
        var reversalId = UUID.randomUUID();
        var originalLedgerTransactionId = UUID.randomUUID();
        var reversalLedgerTransactionId = UUID.randomUUID();
        reverseTransferUseCase.nextResponse = new ReversalResponse(
                reversalId,
                transferId,
                originalLedgerTransactionId,
                reversalLedgerTransactionId,
                ReversalStatus.COMPLETED,
                ReversalReasonCode.DUPLICATE_TRANSFER,
                "duplicate request",
                dev.kavrin.banking_ledger.transfer.domain.model.RequestedByActorType.OPS_ADMIN,
                "OPS_ADMIN",
                "ops-user-1",
                "corr-reversal",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        mockMvc.perform(post("/api/v1/transfers/{transferId}/reverse", transferId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", " corr-reversal ")
                        .header("X-Actor-Type", "OPS_ADMIN")
                        .header("X-Actor-Role", "OPS_ADMIN")
                        .header("X-Actor-Id", " ops-user-1 ")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reasonCode", "DUPLICATE_TRANSFER",
                                "reasonDetail", " duplicate request "
                        ))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/transfers/" + transferId + "/reversal")))
                .andExpect(jsonPath("$.id").value(reversalId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(reverseTransferUseCase.lastCommand.transferId()).isEqualTo(transferId);
        assertThat(reverseTransferUseCase.lastCommand.reasonCode()).isEqualTo(ReversalReasonCode.DUPLICATE_TRANSFER);
        assertThat(reverseTransferUseCase.lastCommand.reasonDetail()).isEqualTo("duplicate request");
        assertThat(reverseTransferUseCase.lastCommand.actorType().name()).isEqualTo("OPS_ADMIN");
        assertThat(reverseTransferUseCase.lastCommand.actorRole().name()).isEqualTo("OPS_ADMIN");
        assertThat(reverseTransferUseCase.lastCommand.actorId()).isEqualTo("ops-user-1");
        assertThat(reverseTransferUseCase.lastCommand.correlationId()).isEqualTo("corr-reversal");
    }

    @Test
    void missingReasonCodeReturnsStructuredValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/transfers/{transferId}/reverse", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "corr-reversal")
                        .header("X-Actor-Type", "OPS_ADMIN")
                        .header("X-Actor-Role", "OPS_ADMIN")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed."));

        assertThat(reverseTransferUseCase.lastCommand).isNull();
    }

    @Test
    void forbiddenActorRoleReturnsStructuredError() throws Exception {
        mockMvc.perform(post("/api/v1/transfers/{transferId}/reverse", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "corr-reversal")
                        .header("X-Actor-Type", "TELLER")
                        .header("X-Actor-Role", "TELLER")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reasonCode", "DUPLICATE_TRANSFER"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_RESOURCE"))
                .andExpect(jsonPath("$.message").value("Actor role is not allowed to reverse transfers."));
    }

    @Test
    void missingCorrelationHeaderReturnsStructuredBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/transfers/{transferId}/reverse", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Actor-Type", "OPS_ADMIN")
                        .header("X-Actor-Role", "OPS_ADMIN")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reasonCode", "DUPLICATE_TRANSFER"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Required request header is missing."))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("X-Correlation-Id"));

        assertThat(reverseTransferUseCase.lastCommand).isNull();
    }

    @Test
    void unsupportedReasonCodeReturnsMalformedRequest() throws Exception {
        mockMvc.perform(post("/api/v1/transfers/{transferId}/reverse", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "corr-reversal")
                        .header("X-Actor-Type", "OPS_ADMIN")
                        .header("X-Actor-Role", "OPS_ADMIN")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reasonCode", "UNKNOWN_REASON"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed."));

        assertThat(reverseTransferUseCase.lastCommand).isNull();
    }

    private static class StubReverseTransferUseCase extends ReverseTransferUseCase {
        private ReverseTransferCommand lastCommand;
        private ReversalResponse nextResponse;

        private StubReverseTransferUseCase() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public ReversalResponse handle(ReverseTransferCommand command) {
            lastCommand = command;
            dev.kavrin.banking_ledger.reversal.domain.policy.ReversalValidationPolicy.validateRequest(command);
            return nextResponse;
        }
    }
}
