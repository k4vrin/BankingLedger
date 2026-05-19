package dev.kavrin.banking_ledger.adjustment.persistence.mapper;

import dev.kavrin.banking_ledger.adjustment.api.dto.AdjustmentResponse;
import dev.kavrin.banking_ledger.adjustment.persistence.AdjustmentRequestEntity;
import org.springframework.stereotype.Component;

@Component
public class AdjustmentResponseMapper {

    public AdjustmentResponse toResponse(AdjustmentRequestEntity adjustment) {
        return new AdjustmentResponse(
                adjustment.getId(),

                adjustment.getLedgerTransaction() == null
                        ? null
                        : adjustment.getLedgerTransaction().getId(),

                adjustment.getReasonCode(),
                adjustment.getReasonDetail(),

                adjustment.getRequestedByActorType(),
                adjustment.getRequestedByActorRole(),
                adjustment.getRequestedByActorId(),
                adjustment.getCorrelationId(),

                adjustment.getStatus(),

                adjustment.getRequestedAt(),
                adjustment.getCompletedAt(),

                adjustment.getFailureReasonCode(),
                adjustment.getFailureReasonDetail(),

                adjustment.getCreatedAt(),
                adjustment.getUpdatedAt()
        );
    }
}