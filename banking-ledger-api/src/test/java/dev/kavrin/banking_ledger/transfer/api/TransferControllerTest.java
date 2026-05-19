package dev.kavrin.banking_ledger.transfer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.idempotency.application.service.IdempotencyKeyValidator;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.GlobalExceptionHandler;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import dev.kavrin.banking_ledger.transfer.api.dto.TransferResponse;
import dev.kavrin.banking_ledger.transfer.application.command.CreateTransferCommand;
import dev.kavrin.banking_ledger.transfer.application.query.GetTransferByIdQuery;
import dev.kavrin.banking_ledger.transfer.application.service.CreateTransferResult;
import dev.kavrin.banking_ledger.transfer.application.service.CreateTransferUseCase;
import dev.kavrin.banking_ledger.transfer.application.service.TransferQueryUseCase;
import dev.kavrin.banking_ledger.transfer.domain.model.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransferControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StubCreateTransferUseCase createTransferUseCase;
    private StubTransferQueryUseCase transferQueryUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        createTransferUseCase = new StubCreateTransferUseCase();
        transferQueryUseCase = new StubTransferQueryUseCase();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TransferController(createTransferUseCase, transferQueryUseCase, new IdempotencyKeyValidator()))
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

    @Test
    void invalidCreateRequestReturnsStructuredValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "transfer-key")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceAccountId", UUID.randomUUID(),
                                "currencyCode", "usd",
                                "amountMinor", 0
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed."));

        assertThat(createTransferUseCase.lastCommand).isNull();
    }

    @Test
    void lockTimeoutReturnsStructuredConflictError() throws Exception {
        createTransferUseCase.nextException = new CannotAcquireLockException("lock timeout");

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "transfer-key")
                        .content(validRequestBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_TRANSFER_CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Transfer could not be completed because the account is currently being modified. Please retry."
                ));
    }

    @Test
    void optimisticLockFailureReturnsStructuredConflictError() throws Exception {
        createTransferUseCase.nextException = new ObjectOptimisticLockingFailureException("AccountEntity", UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "transfer-key")
                        .content(validRequestBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_TRANSFER_CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Transfer could not be completed because the account changed during processing. Please retry."
                ));
    }

    @Test
    void getTransferReturnsTransferResponse() throws Exception {
        var transferId = UUID.randomUUID();
        var sourceAccountId = UUID.randomUUID();
        var destinationAccountId = UUID.randomUUID();
        transferQueryUseCase.nextResponse = new TransferResponse(
                transferId,
                sourceAccountId,
                destinationAccountId,
                TransferStatus.COMPLETED,
                "USD",
                100,
                UUID.randomUUID(),
                "transfer-ref",
                "test transfer",
                null,
                null,
                null,
                null,
                null,
                null
        );

        mockMvc.perform(get("/api/v1/transfers/{transferId}", transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transferId.toString()))
                .andExpect(jsonPath("$.sourceAccountId").value(sourceAccountId.toString()))
                .andExpect(jsonPath("$.destinationAccountId").value(destinationAccountId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(transferQueryUseCase.lastQuery.transferId()).isEqualTo(transferId);
    }

    @Test
    void missingTransferLookupReturnsStructuredNotFoundError() throws Exception {
        var transferId = UUID.randomUUID();
        transferQueryUseCase.notFound = true;

        mockMvc.perform(get("/api/v1/transfers/{transferId}", transferId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Transfer not found."));
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
        private RuntimeException nextException;

        private StubCreateTransferUseCase() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public CreateTransferResult handle(CreateTransferCommand command) {
            lastCommand = command;
            if (nextException != null) {
                throw nextException;
            }
            return nextResult;
        }
    }

    private static class StubTransferQueryUseCase extends TransferQueryUseCase {
        private GetTransferByIdQuery lastQuery;
        private TransferResponse nextResponse;

        private StubTransferQueryUseCase() {
            super(null, null);
        }

        @Override
        public TransferResponse getById(GetTransferByIdQuery query) {
            lastQuery = query;
            if (notFound) {
                throw new ResourceNotFoundException(
                        ApiErrorCode.Business.RESOURCE_NOT_FOUND,
                        "Transfer not found: " + query.transferId(),
                        "Transfer not found."
                );
            }
            return nextResponse;
        }

        private boolean notFound;
    }
}
