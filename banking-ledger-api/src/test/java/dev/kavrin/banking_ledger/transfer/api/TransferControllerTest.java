package dev.kavrin.banking_ledger.transfer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.idempotency.application.service.IdempotencyKeyValidator;
import dev.kavrin.banking_ledger.shared.error.GlobalExceptionHandler;
import dev.kavrin.banking_ledger.transfer.application.command.CreateTransferCommand;
import dev.kavrin.banking_ledger.transfer.application.service.CreateTransferResult;
import dev.kavrin.banking_ledger.transfer.application.service.CreateTransferUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StubCreateTransferUseCase createTransferUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        createTransferUseCase = new StubCreateTransferUseCase();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TransferController(createTransferUseCase, new IdempotencyKeyValidator()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void missingIdempotencyKeyIsRejectedBeforeCreatingTransfer() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Idempotency-Key header is required"));

        assertThat(createTransferUseCase.lastCommand).isNull();
    }

    @Test
    void createTransferReadsIdempotencyHeaderAndReturnsCreatedResponse() throws Exception {
        var transferId = UUID.randomUUID();
        createTransferUseCase.nextResult = new CreateTransferResult(
                201,
                "{\"id\":\"" + transferId + "\"}",
                transferId,
                false
        );

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", " transfer-key ")
                        .header("X-Actor-Type", "SYSTEM")
                        .content(validRequestBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/transfers/" + transferId)))
                .andExpect(content().json("{\"id\":\"" + transferId + "\"}"));

        assertThat(createTransferUseCase.lastCommand.idempotencyKey()).isEqualTo("transfer-key");
        assertThat(createTransferUseCase.lastCommand.actorType().name()).isEqualTo("SYSTEM");
        assertThat(createTransferUseCase.lastCommand.amountMinor()).isEqualTo(100);
    }

    @Test
    void idempotencyReplayReturnsOkWithoutLocationHeader() throws Exception {
        var transferId = UUID.randomUUID();
        createTransferUseCase.nextResult = new CreateTransferResult(
                200,
                "{\"id\":\"" + transferId + "\"}",
                transferId,
                true
        );

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "transfer-key")
                        .content(validRequestBody()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().json("{\"id\":\"" + transferId + "\"}"));
    }

    private String validRequestBody() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "sourceAccountId", UUID.randomUUID(),
                "destinationAccountId", UUID.randomUUID(),
                "currencyCode", "USD",
                "amountMinor", 100
        ));
    }

    private static class StubCreateTransferUseCase extends CreateTransferUseCase {
        private CreateTransferCommand lastCommand;
        private CreateTransferResult nextResult = new CreateTransferResult(201, "{}", UUID.randomUUID(), false);

        private StubCreateTransferUseCase() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public CreateTransferResult handle(CreateTransferCommand command) {
            lastCommand = command;
            return nextResult;
        }
    }
}
