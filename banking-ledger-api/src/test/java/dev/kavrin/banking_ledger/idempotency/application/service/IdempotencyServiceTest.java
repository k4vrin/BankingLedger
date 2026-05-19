package dev.kavrin.banking_ledger.idempotency.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.idempotency.domain.model.IdempotencyOperationScope;
import dev.kavrin.banking_ledger.idempotency.persistence.entity.IdempotencyRecordEntity;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import dev.kavrin.banking_ledger.shared.error.ConflictException;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;
import dev.kavrin.banking_ledger.transfer.application.command.CreateTransferCommand;
import dev.kavrin.banking_ledger.transfer.domain.model.RequestedByActorType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyServiceTest {

    @Test
    void validatorRejectsMissingBlankAndOverLengthKeys() {
        var validator = new IdempotencyKeyValidator();

        assertThatThrownBy(() -> validator.validateAndNormalize(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Idempotency-Key header is required");
        assertThatThrownBy(() -> validator.validateAndNormalize("   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Idempotency-Key header is required");
        assertThatThrownBy(() -> validator.validateAndNormalize("a".repeat(129)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Idempotency-Key must be at most 128 characters");

        assertThat(validator.validateAndNormalize(" transfer-key ")).isEqualTo("transfer-key");
    }

    @Test
    void hasherNormalizesEquivalentTransferCommands() {
        var hasher = new TransferRequestHasher(new ObjectMapper());
        var sourceAccountId = UUID.randomUUID();
        var destinationAccountId = UUID.randomUUID();

        var firstHash = hasher.hash(new CreateTransferCommand(
                sourceAccountId,
                destinationAccountId,
                CurrencyCode.of("usd"),
                100,
                " ref-1 ",
                " transfer ",
                "idem-1",
                RequestedByActorType.SYSTEM,
                "corr-1"
        ));
        var secondHash = hasher.hash(new CreateTransferCommand(
                sourceAccountId,
                destinationAccountId,
                CurrencyCode.of("USD"),
                100,
                "ref-1",
                "transfer",
                "idem-2",
                RequestedByActorType.SYSTEM,
                "corr-2"
        ));

        assertThat(firstHash).isEqualTo(secondHash);
    }

    @Test
    void hashMismatchIsRejectedAsIdempotencyConflict() {
        var existing = IdempotencyRecordEntity.builder()
                .operationScope(IdempotencyOperationScope.TRANSFER_CREATE.name())
                .idempotencyKey("transfer-key")
                .requestHash("original-hash")
                .responseStatus(201)
                .responseBody("{\"id\":\"original\"}")
                .resourceType("TRANSFER")
                .resourceId(UUID.randomUUID())
                .createdAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusHours(24))
                .build();

        assertThatThrownBy(() -> new IdempotencyService(null).rejectIfHashMismatch(existing, "different-hash"))
                .isInstanceOfSatisfying(ConflictException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.INVALID_IDEMPOTENCY_KEY));
    }
}
