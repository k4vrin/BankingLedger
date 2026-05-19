package dev.kavrin.banking_ledger.idempotency.application.service;

import dev.kavrin.banking_ledger.idempotency.domain.model.IdempotencyOperationScope;
import dev.kavrin.banking_ledger.idempotency.persistence.entity.IdempotencyRecordEntity;
import dev.kavrin.banking_ledger.idempotency.persistence.repository.IdempotencyRecordRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String TRANSFER_RESOURCE_TYPE = "TRANSFER";

    private final IdempotencyRecordRepository repository;

    public Optional<IdempotencyRecordEntity> findTransferCreate(String key) {
        return repository.findByOperationScopeAndIdempotencyKey(
                IdempotencyOperationScope.TRANSFER_CREATE.name(),
                key
        );
    }

    public void rejectIfHashMismatch(IdempotencyRecordEntity existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ConflictException(
                    ApiErrorCode.Business.INVALID_IDEMPOTENCY_KEY,
                    "Idempotency-Key was already used with a different request"
            );
        }
    }

    public IdempotencyRecordEntity createTransferCreateRecord(
            String key,
            String requestHash,
            String responseBody,
            int statusCode,
            UUID transferId
    ) {
        var now = OffsetDateTime.now();
        IdempotencyRecordEntity entity = IdempotencyRecordEntity.builder()
                .operationScope(IdempotencyOperationScope.TRANSFER_CREATE.name())
                .idempotencyKey(key)
                .requestHash(requestHash)
                .responseBody(responseBody)
                .responseStatus(statusCode)
                .resourceType(TRANSFER_RESOURCE_TYPE)
                .resourceId(transferId)
                .createdAt(now)
                .expiresAt(now.plusHours(24))
                .build();

        return repository.save(entity);
    }
}
